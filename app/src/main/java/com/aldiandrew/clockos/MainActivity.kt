package com.aldiandrew.clockos

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
                    onSettingsChanged = { refreshClockIcon() }
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

    private fun clockIconResId(sizeSp: Float): Int {
        val size = sizeSp.roundToInt().coerceIn(10, 22)
        return resources.getIdentifier(
            "status_bar_clock_$size",
            "drawable",
            packageName
        )
    }

    private fun clockIconLevel(settings: ClockSettings): Int {
        var level = 0

        if (settings.format24) level = level or 0x01
        if (settings.showSeconds) level = level or 0x02
        if (settings.showDate) level = level or 0x04
        if (settings.showDay) level = level or 0x08

        level = level or when {
            settings.weight >= 650 -> (3 shl 4)
            settings.weight >= 450 -> (2 shl 4)
            settings.weight >= 350 -> (1 shl 4)
            else -> 0
        }

        return level
    }

    private fun setSystemUiClock(settings: ClockSettings, callback: (Boolean, String) -> Unit) {
        val iconId = clockIconResId(settings.sizeSp)
        if (iconId == 0) {
            callback(false, "ClockOS drawable resource not found")
            return
        }

        val level = clockIconLevel(settings)

        shell.execute(
            "cmd statusbar set-icon clockos $packageName $iconId $level ClockOS"
        ) { result ->
            callback(
                result.startsWith("exit=0"),
                result
            )
        }
    }

    private fun applyClock(enable: Boolean) {
        if (!shizukuReady) {
            Toast.makeText(this, "Shizuku is not ready", Toast.LENGTH_SHORT).show()
            return
        }

        if (!enable) {
            shell.execute("cmd statusbar remove-icon clockos") { removeResult ->
                if (!removeResult.startsWith("exit=0")) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            removeResult.take(300),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@execute
                }

                shell.execute("cmd statusbar send-disable-flag none") { restoreResult ->
                    runOnUiThread {
                        if (restoreResult.startsWith("exit=0")) {
                            clockEnabled = false
                            Toast.makeText(
                                this,
                                "Native clock restored",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                restoreResult.take(300),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
            return
        }

        setSystemUiClock(prefs.load()) { iconOk, iconResult ->
            if (!iconOk) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        iconResult.take(300),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@setSystemUiClock
            }

            shell.execute("cmd statusbar send-disable-flag clock") { clockResult ->
                runOnUiThread {
                    if (clockResult.startsWith("exit=0")) {
                        clockEnabled = true
                        Toast.makeText(
                            this,
                            "ClockOS enabled",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        shell.execute("cmd statusbar remove-icon clockos") {}
                        Toast.makeText(
                            this,
                            clockResult.take(300),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun refreshClockIcon() {
        if (!clockEnabled || !shizukuReady) return

        setSystemUiClock(prefs.load()) { ok, result ->
            if (!ok) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        result.take(300),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

private fun Float.roundToInt(): Int = kotlin.math.round(this).toInt()

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
        topBar = { TopAppBar(title = { Text("ClockOS") }) }
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
                if (shizukuReady) "Shizuku: ready"
                else "Shizuku: not ready"
            )

            Text(
                if (clockEnabled) {
                    "ClockOS: active"
                } else {
                    "ClockOS: native clock mode"
                },
                style = MaterialTheme.typography.bodyMedium
            )

            if (!shizukuReady) {
                Button(onClick = onRequestShizuku) {
                    Text("Connect Shizuku")
                }
            }

            Text(
                "ClockOS uses the native SystemUI status-bar layout and tint. " +
                    "No manual overlay permission is required.",
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

            Text("Size: %.0fsp".format(settings.sizeSp))

            Slider(
                value = settings.sizeSp,
                onValueChange = {
                    settings = settings.copy(sizeSp = it)
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
