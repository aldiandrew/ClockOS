package com.aldiandrew.clockos

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import com.aldiandrew.clockos.shizuku.IUserService

class ShizukuShell(private val context: Context) {
    companion object { private const val REQUEST_CODE = 1001 }

    private var service: IUserService? = null
    private var bound = false
    private var pending: ((String) -> Unit)? = null

    private val connection = object : Shizuku.ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: android.os.IBinder) {
            service = IUserService.Stub.asInterface(binder)
            bound = true
            pending?.let { callback ->
                pending = null
                callback("connected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
        }
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun requestPermission() {
        if (!Shizuku.isPreV11() && !hasPermission()) Shizuku.requestPermission(REQUEST_CODE)
    }

    fun isAvailable(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun execute(command: String, callback: (String) -> Unit) {
        if (!isAvailable()) {
            callback("Shizuku is not running")
            return
        }
        if (!hasPermission()) {
            requestPermission()
            callback("Shizuku permission required")
            return
        }

        val current = service
        if (current != null) {
            Thread {
                val result = try { current.exec(command) } catch (t: Throwable) { "error=$t" }
                callback(result)
            }.start()
            return
        }

        pending = { result ->
            if (result == "connected") {
                Thread {
                    val output = try { service?.exec(command) ?: "service unavailable" }
                    catch (t: Throwable) { "error=$t" }
                    callback(output)
                }.start()
            } else callback(result)
        }

        val args = UserServiceArgs(
            ComponentName(context, com.aldiandrew.clockos.shizuku.UserService::class.java)
        ).apply {
            daemon = false
            tag = "clockos-shell"
            version = 1
        }

        try {
            Shizuku.bindUserService(args, connection)
        } catch (t: Throwable) {
            pending = null
            callback("bind error=$t")
        }
    }

    fun unbind() {
        if (!bound) return
        try {
            val args = UserServiceArgs(
                ComponentName(context, com.aldiandrew.clockos.shizuku.UserService::class.java)
            ).apply {
                daemon = false
                tag = "clockos-shell"
                version = 1
            }
            Shizuku.unbindUserService(args, connection, true)
        } catch (_: Throwable) {}
        service = null
        bound = false
    }
}
