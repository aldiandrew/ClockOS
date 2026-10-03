package com.aldiandrew.clockos

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import com.aldiandrew.clockos.shizuku.IUserService

class ShizukuShell(private val context: Context) {
    companion object { private const val REQUEST_CODE = 1001 }

    private var service: IUserService? = null
    private var bound = false
    private var pendingCommand: String? = null
    private var pendingCallback: ((String) -> Unit)? = null

    private val serviceArgs = UserServiceArgs(
        ComponentName(context, com.aldiandrew.clockos.shizuku.UserService::class.java)
    )
        .daemon(false)
        .tag("clockos-shell")
        .version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IUserService.Stub.asInterface(binder)
            bound = true
            val command = pendingCommand
            val callback = pendingCallback
            pendingCommand = null
            pendingCallback = null

            if (command != null && callback != null) {
                Thread {
                    val result = try {
                        service?.exec(command) ?: "service unavailable"
                    } catch (t: Throwable) {
                        "error=$t"
                    }
                    callback(result)
                }.start()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            bound = false
        }
    }

    fun hasPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun requestPermission() {
        if (!Shizuku.isPreV11() && !hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

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
                val result = try {
                    current.exec(command)
                } catch (t: Throwable) {
                    "error=$t"
                }
                callback(result)
            }.start()
            return
        }

        pendingCommand = command
        pendingCallback = callback

        try {
            Shizuku.bindUserService(serviceArgs, connection)
        } catch (t: Throwable) {
            pendingCommand = null
            pendingCallback = null
            callback("bind error=$t")
        }
    }

    fun unbind() {
        try {
            Shizuku.unbindUserService(serviceArgs, connection, true)
        } catch (_: Throwable) {
        }
        service = null
        bound = false
        pendingCommand = null
        pendingCallback = null
    }
}
