package com.aldiandrew.clockos

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs
import com.aldiandrew.clockos.shizuku.IUserService
import java.util.ArrayDeque

class ShizukuShell(private val context: Context) {

    companion object {
        private const val REQUEST_CODE = 1001
    }

    private data class Command(
        val command: String,
        val callback: (String) -> Unit
    )

    private val lock = Any()
    private val queue = ArrayDeque<Command>()

    private var service: IUserService? = null
    private var binding = false
    private var running = false

    private val serviceArgs = UserServiceArgs(
        ComponentName(
            context,
            com.aldiandrew.clockos.shizuku.UserService::class.java
        )
    )
        .daemon(false)
        .tag("clockos-shell")
        .processNameSuffix("clockos_shell")
        .version(2)

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName,
            binder: IBinder
        ) {
            synchronized(lock) {
                service =
                    IUserService.Stub.asInterface(binder)
                binding = false
                runNextLocked()
            }
        }

        override fun onServiceDisconnected(
            name: ComponentName
        ) {
            synchronized(lock) {
                service = null
                binding = false
                running = false
            }
        }
    }

    fun hasPermission(): Boolean =
        try {
            Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

    fun requestPermission() {
        if (!Shizuku.isPreV11() && !hasPermission()) {
            Shizuku.requestPermission(REQUEST_CODE)
        }
    }

    fun isAvailable(): Boolean =
        try {
            Shizuku.pingBinder()
        } catch (_: Throwable) {
            false
        }

    fun execute(
        command: String,
        callback: (String) -> Unit
    ) {
        if (!isAvailable()) {
            callback("Shizuku is not running")
            return
        }

        if (!hasPermission()) {
            requestPermission()
            callback("Shizuku permission required")
            return
        }

        synchronized(lock) {
            queue.addLast(
                Command(command, callback)
            )

            if (service != null) {
                runNextLocked()
            } else {
                bindLocked()
            }
        }
    }

    private fun bindLocked() {
        if (binding || service != null) return

        binding = true

        try {
            Shizuku.bindUserService(
                serviceArgs,
                connection
            )
        } catch (t: Throwable) {
            binding = false
            val error = "bind error=$t"

            while (queue.isNotEmpty()) {
                queue.removeFirst().callback(error)
            }
        }
    }

    private fun runNextLocked() {
        if (running) return

        val current = service ?: return
        if (queue.isEmpty()) return

        val item = queue.removeFirst()
        running = true

        Thread {
            val result =
                try {
                    current.exec(item.command)
                } catch (t: Throwable) {
                    "error=$t"
                }

            try {
                item.callback(result)
            } catch (_: Throwable) {
            }

            synchronized(lock) {
                running = false

                if (service != null) {
                    runNextLocked()
                } else if (queue.isNotEmpty()) {
                    bindLocked()
                }
            }
        }.start()
    }

    fun unbind() {
        synchronized(lock) {
            queue.clear()
            running = false

            try {
                Shizuku.unbindUserService(
                    serviceArgs,
                    connection,
                    true
                )
            } catch (_: Throwable) {
            }

            service = null
            binding = false
        }
    }
}
