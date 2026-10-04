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
                    onRequestShizuku = { requestShizuku() },
                    onStart = { applyClock(true) },
                    onStop = { applyClock(false) }
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
            Toast.makeText(this, "Shizuku is not running", Toast.LENGTH_SHORT).show()
            return
        }

        if (shell.hasPermission()) {
            refreshShizukuState()
            return
        }

        shell.requestPermission()
    }

    private fun applyClock(enable: Boolean) {
        if (!enable) {
            shell.execute("cmd statusbar send-disable-flag none") { result ->
                runOnUiThread {
                    if (result.startsWith("exit=0")) {
                        stopService(Intent(this, ClockOverlayService::class.java))
                        Toast.makeText(this, "Native clock restored", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, result.take(300), Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }

        shell.execute(
            "appops set $packageName android:system_alert_window allow"
        ) { appOpsResult ->
            if (!appOpsResult.startsWith("exit=0")) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Shizuku could not enable ClockOS window:\n" +
                            appOpsResult.take(250),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@execute
            }

            shell.execute("cmd statusbar send-disable-flag clock") { clockResult ->
                runOnUiThread {
                    if (clockResult.startsWith("exit=0")) {
                        try {
                            startForegroundService(
                                Intent(this, ClockOverlayService::class.java)
                            )
                        } catch (t: Throwable) {
                            Toast.makeText(
                                this,
                                "Could not start ClockOS: " +
                                    (t.message ?: t.javaClass.simpleName),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        Toast.makeText(this, clockResult.take(300), Toast.LENGTH_LONG).show()
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
    onRequestShizuku: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var settings by remember { mutableStateOf(prefs.load()) }

    fun save(key: String, value: Any) {
        prefs.set(key, value)
        settings = prefs.load()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("ClockOS") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(if (shizukuReady) "Shizuku: ready" else "Shizuku: not ready")

            if (!shizukuReady) {
                Button(onClick = onRequestShizuku) {
                    Text("Connect Shizuku")
                }
            }

            Text(
                "ClockOS uses Shizuku. No manual Display over other apps setup is required.",
                style = MaterialTheme.typography.bodySmall
            )

            SettingSwitch("24-hour", settings.format24) { save("format24", it) }
            SettingSwitch("Seconds", settings.showSeconds) { save("showSeconds", it) }
            SettingSwitch("Date", settings.showDate) { save("showDate", it) }
            SettingSwitch("Day", settings.showDay) { save("showDay", it) }

            Text("Size: %.0fsp".format(settings.sizeSp))
            Slider(
                value = settings.sizeSp,
                onValueChange = { save("sizeSp", it) },
                valueRange = 10f..22f
            )

            Text("Position")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("left", "center", "right").forEach { pos ->
                    FilterChip(
                        selected = settings.position == pos,
                        onClick = { save("position", pos) },
                        label = { Text(pos.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Text("Color")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system", "white", "black").forEach { mode ->
                    FilterChip(
                        selected = settings.colorMode == mode,
                        onClick = { save("colorMode", mode) },
                        label = { Text(mode.replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = shizukuReady
            ) { Text("Enable ClockOS") }

            OutlinedButton(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth(),
                enabled = shizukuReady
            ) { Text("Restore Native Clock") }
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
