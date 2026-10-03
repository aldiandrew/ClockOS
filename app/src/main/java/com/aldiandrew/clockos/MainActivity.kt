package com.aldiandrew.clockos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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

class MainActivity : ComponentActivity() {
    private lateinit var shell: ShizukuShell
    private lateinit var prefs: ClockPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        shell = ShizukuShell(this)
        prefs = ClockPrefs(this)
        setContent {
            ClockOSTheme {
                ClockScreen(
                    prefs = prefs,
                    shizukuReady = shell.isAvailable() && shell.hasPermission(),
                    onRequestShizuku = { shell.requestPermission() },
                    onOverlay = { openOverlaySettings() },
                    onStart = { applyClock(true) },
                    onStop = { applyClock(false) }
                )
            }
        }
    }

    private fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun applyClock(enable: Boolean) {
        val command = if (enable) "cmd statusbar send-disable-flag clock"
        else "cmd statusbar send-disable-flag none"
        shell.execute(command) { result ->
            runOnUiThread {
                if (result.startsWith("exit=0")) {
                    if (enable && Settings.canDrawOverlays(this)) {
                        startForegroundService(Intent(this, ClockOverlayService::class.java))
                    } else if (enable) {
                        openOverlaySettings()
                    } else {
                        stopService(Intent(this, ClockOverlayService::class.java))
                        Toast.makeText(this, "Native clock restored", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, result.take(200), Toast.LENGTH_LONG).show()
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
    onOverlay: () -> Unit,
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
            if (!shizukuReady) Button(onClick = onRequestShizuku) { Text("Connect Shizuku") }
            OutlinedButton(onClick = onOverlay, modifier = Modifier.fillMaxWidth()) {
                Text("Allow overlay")
            }

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
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
