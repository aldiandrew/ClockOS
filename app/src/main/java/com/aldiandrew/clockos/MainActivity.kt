package com.aldiandrew.clockos

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aldiandrew.clockos.ui.theme.ClockOSTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private lateinit var shell: ShizukuShell
    private lateinit var prefs: ClockPrefs

    private var shizukuReady by mutableStateOf(false)
    private var clockEnabled by mutableStateOf(false)

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ ->
            refreshShizukuState()
        }

    private val binderListener = object : Shizuku.OnBinderReceivedListener {
        override fun onBinderReceived() {
            refreshShizukuState()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        refreshShizukuState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        shell = ShizukuShell(this)
        prefs = ClockPrefs(this)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        refreshShizukuState()

        setContent {
            ClockOSTheme {
                ClockScreen(
                    prefs = prefs,
                    shizukuReady = shizukuReady,
                    clockEnabled = clockEnabled,
                    onRequestShizuku = { requestShizuku() },
                    onStart = { applyClock(true) },
                    onStop = { applyClock(false) },
                    onSettingsChanged = {
                        if (clockEnabled) {
                            sendSettingsChangedBroadcast()
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshShizukuState()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permissionListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderReceivedListener(binderListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderDeadListener(binderDeadListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun refreshShizukuState() {
        shizukuReady = shell.isAvailable() && shell.hasPermission()
    }

    private fun requestShizuku() {
        if (!shell.isAvailable()) {
            Toast.makeText(
                this,
                "Shizuku is not running",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (shell.hasPermission()) {
            refreshShizukuState()
            return
        }

        shell.requestPermission()
    }

    private fun sendSettingsChangedBroadcast() {
        sendBroadcast(
            Intent(this, ClockOverlayService::class.java).apply {
                action = ClockOverlayService.ACTION_SETTINGS_CHANGED
            }
        )
    }

    private fun applyClock(enable: Boolean) {
        if (!shizukuReady) {
            Toast.makeText(
                this,
                "Shizuku is not ready",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!enable) {
            stopService(
                Intent(this, ClockOverlayService::class.java)
            )
            clockEnabled = false

            Toast.makeText(
                this,
                "ClockOS stopped",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        shell.execute(
            "appops set $packageName " +
                "android:system_alert_window allow"
        ) { result ->
            runOnUiThread {
                if (!result.startsWith("exit=0")) {
                    Toast.makeText(
                        this,
                        result.take(300),
                        Toast.LENGTH_LONG
                    ).show()
                    return@runOnUiThread
                }

                // Keep the real SystemUI clock and notification layout intact.
                // ClockOS is placed exactly over the clock's native area.
                shell.execute(
                    "cmd statusbar send-disable-flag none"
                ) { restoreResult ->
                    runOnUiThread {
                        if (!restoreResult.startsWith("exit=0")) {
                            Toast.makeText(
                                this,
                                restoreResult.take(300),
                                Toast.LENGTH_LONG
                            ).show()
                            return@runOnUiThread
                        }

                        try {
                            startForegroundService(
                                Intent(
                                    this,
                                    ClockOverlayService::class.java
                                )
                            )

                            clockEnabled = true

                            Toast.makeText(
                                this,
                                "ClockOS enabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        } catch (t: Throwable) {
                            Toast.makeText(
                                this,
                                "Could not start ClockOS: " +
                                    (t.message
                                        ?: t.javaClass.simpleName),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockScreen(
    prefs: ClockPrefs,
    shizukuReady: Boolean,
    clockEnabled: Boolean,
    onRequestShizuku: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettingsChanged: () -> Unit
) {
    var settings by remember { mutableStateOf(prefs.load()) }

    fun save(key: String, value: Any) {
        prefs.set(key, value)
        settings = prefs.load()
        onSettingsChanged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ClockOS") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                if (shizukuReady) {
                    "Shizuku: ready"
                } else {
                    "Shizuku: not ready"
                }
            )

            Text(
                if (clockEnabled) {
                    "ClockOS: active"
                } else {
                    "ClockOS: stopped"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            if (!shizukuReady) {
                Button(
                    onClick = onRequestShizuku
                ) {
                    Text("Connect Shizuku")
                }
            }

            Text(
                "ClockOS follows the native SystemUI clock area, font tint, " +
                    "and status-bar alignment. Notifications remain managed by SystemUI.",
                style = MaterialTheme.typography.bodySmall
            )

            SettingSwitch(
                "24-hour",
                settings.format24
            ) {
                save("format24", it)
            }

            SettingSwitch(
                "Seconds",
                settings.showSeconds
            ) {
                save("showSeconds", it)
            }

            SettingSwitch(
                "Date",
                settings.showDate
            ) {
                save("showDate", it)
            }

            SettingSwitch(
                "Day",
                settings.showDay
            ) {
                save("showDay", it)
            }

            Text(
                "Size: %.0fsp".format(settings.sizeSp)
            )

            Slider(
                value = settings.sizeSp,
                onValueChange = {
                    settings = settings.copy(
                        sizeSp = it
                    )
                    prefs.set("sizeSp", it)
                },
                onValueChangeFinished = {
                    settings = prefs.load()
                    onSettingsChanged()
                },
                valueRange = 10f..22f
            )

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = shizukuReady
            ) {
                Text("Enable ClockOS")
            }

            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                enabled = shizukuReady
            ) {
                Text("Restore Native Clock")
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
