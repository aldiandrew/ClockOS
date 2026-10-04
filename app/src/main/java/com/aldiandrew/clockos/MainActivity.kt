package com.aldiandrew.clockos

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aldiandrew.clockos.ui.theme.ClockOSTheme
import rikka.shizuku.Shizuku

private val CLOCKOS_DATE_FORMATS =
    listOf(
        "dd/MM",
        "dd/MM/yy",
        "yyyy-MM-dd",
        "dd-MM-yyyy",
        "MMM dd",
        "EEE",
        "EEE dd",
        "EEE dd/MM",
        "EEE dd MMM",
        "EEE MMM dd",
        "EEEE dd/MM",
        "EEEE MM/dd",
        "CUSTOM"
    )

private val CLOCKOS_DATE_STYLES =
    listOf(
        "Normal",
        "lowercase",
        "UPPERCASE"
    )

private val CLOCKOS_AM_PM_STYLES =
    listOf(
        "Hidden",
        "Normal",
        "Small"
    )

class MainActivity : ComponentActivity() {
    private lateinit var shell: ShizukuShell
    private lateinit var prefs: ClockPrefs

    private var shizukuReady by mutableStateOf(false)
    private var clockEnabled by mutableStateOf(false)

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ ->
            refreshShizukuState()
        }

    private val binderListener =
        object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() {
                refreshShizukuState()
            }
        }

    private val binderDeadListener =
        Shizuku.OnBinderDeadListener {
            refreshShizukuState()
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        shell = ShizukuShell(this)
        prefs = ClockPrefs(this)

        Shizuku.addRequestPermissionResultListener(
            permissionListener
        )
        Shizuku.addBinderReceivedListener(
            binderListener
        )
        Shizuku.addBinderDeadListener(
            binderDeadListener
        )

        refreshShizukuState()

        setContent {
            ClockOSTheme {
                ClockScreen(
                    prefs = prefs,
                    shizukuReady = shizukuReady,
                    clockEnabled = clockEnabled,
                    onRequestShizuku = {
                        requestShizuku()
                    },
                    onStart = {
                        applyClock(true)
                    },
                    onStop = {
                        applyClock(false)
                    },
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
        try {
            Shizuku.removeRequestPermissionResultListener(
                permissionListener
            )
        } catch (_: Throwable) {
        }

        try {
            Shizuku.removeBinderReceivedListener(
                binderListener
            )
        } catch (_: Throwable) {
        }

        try {
            Shizuku.removeBinderDeadListener(
                binderDeadListener
            )
        } catch (_: Throwable) {
        }

        super.onDestroy()
    }

    private fun refreshShizukuState() {
        shizukuReady =
            shell.isAvailable() &&
                shell.hasPermission()
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
        try {
            startService(
                Intent(
                    this,
                    ClockOverlayService::class.java
                ).apply {
                    action =
                        ClockOverlayService.ACTION_SETTINGS_CHANGED
                }
            )
        } catch (t: Throwable) {
            Toast.makeText(
                this,
                "Could not update ClockOS: " +
                    (t.message
                        ?: t.javaClass.simpleName),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun applyClock(
        enable: Boolean
    ) {
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
                Intent(
                    this,
                    ClockOverlayService::class.java
                )
            )

            // Restore the native clock immediately. The service also performs
            // this restoration from onDestroy as a second safety net.
            shell.execute(
                "cmd statusbar send-disable-flag none"
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

                // Disable the native SystemUI clock before starting
                // the replacement overlay, so two clocks can never be
                // visible at the same time.
                shell.execute(
                    "cmd statusbar send-disable-flag clock"
                ) { disableResult ->
                    runOnUiThread {
                        if (
                            !disableResult
                                .startsWith("exit=0")
                        ) {
                            Toast.makeText(
                                this,
                                disableResult.take(300),
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
                            // Never leave the stock clock disabled if the
                            // replacement service could not be started.
                            shell.execute(
                                "cmd statusbar send-disable-flag none"
                            )

                            Toast.makeText(
                                this,
                                "Could not start ClockOS: " +
                                    (
                                        t.message
                                            ?: t.javaClass.simpleName
                                    ),
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
    var settings by remember {
        mutableStateOf(
            prefs.load()
        )
    }

    var customDateDialog by remember {
        mutableStateOf(false)
    }

    var customDateInput by remember {
        mutableStateOf(
            settings.customDateFormat
        )
    }

    fun save(
        key: String,
        value: Any
    ) {
        prefs.set(
            key,
            value
        )

        settings =
            prefs.load()

        onSettingsChanged()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("ClockOS")
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {
            Spacer(
                Modifier.height(8.dp)
            )

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
                style =
                    MaterialTheme.typography.bodyMedium
            )

            if (!shizukuReady) {
                Button(
                    onClick = onRequestShizuku
                ) {
                    Text("Connect Shizuku")
                }
            }

            Text(
                "ClockOS uses the native SystemUI clock slot, " +
                    "font family, and status-bar tint. " +
                    "The native glyph is visually masked so " +
                    "notifications keep their original layout.",
                style =
                    MaterialTheme.typography.bodySmall
            )

            SettingSwitch(
                label = "24-hour",
                checked = settings.format24
            ) {
                save(
                    "format24",
                    it
                )
            }

            SettingSwitch(
                label = "Seconds",
                checked = settings.showSeconds
            ) {
                save(
                    "showSeconds",
                    it
                )
            }

            SettingSwitch(
                label = "Date",
                checked = settings.showDate
            ) {
                save(
                    "showDate",
                    it
                )
            }

            if (settings.showDate) {
                SettingDropdown(
                    label = "Date format",
                    selected =
                        dateFormatLabel(
                            settings
                        ),
                    options =
                        CLOCKOS_DATE_FORMATS.map {
                            if (it == "CUSTOM") {
                                "Custom"
                            } else {
                                it
                            }
                        },
                    enabled = true
                ) { selected ->
                    val raw =
                        if (selected == "Custom") {
                            "CUSTOM"
                        } else {
                            selected
                        }

                    if (raw == "CUSTOM") {
                        customDateInput =
                            settings.customDateFormat
                        customDateDialog = true
                    } else {
                        save(
                            "dateFormat",
                            raw
                        )
                    }
                }

                SettingDropdown(
                    label = "Date style",
                    selected =
                        CLOCKOS_DATE_STYLES[
                            settings.dateStyle
                        ],
                    options =
                        CLOCKOS_DATE_STYLES,
                    enabled = true
                ) { selected ->
                    save(
                        "dateStyle",
                        CLOCKOS_DATE_STYLES
                            .indexOf(selected)
                            .coerceAtLeast(0)
                    )
                }
            }

            SettingSwitch(
                label = "Day",
                checked = settings.showDay
            ) {
                save(
                    "showDay",
                    it
                )
            }

            if (!settings.format24) {
                SettingDropdown(
                    label = "AM/PM",
                    selected =
                        CLOCKOS_AM_PM_STYLES[
                            settings.amPmStyle
                        ],
                    options =
                        CLOCKOS_AM_PM_STYLES,
                    enabled = true
                ) { selected ->
                    save(
                        "amPmStyle",
                        CLOCKOS_AM_PM_STYLES
                            .indexOf(selected)
                            .coerceAtLeast(0)
                    )
                }
            }

            Text(
                "Size: %.0fsp".format(
                    settings.sizeSp
                )
            )

            Slider(
                value = settings.sizeSp,
                onValueChange = {
                    settings =
                        settings.copy(
                            sizeSp = it
                        )

                    prefs.set(
                        "sizeSp",
                        it
                    )
                },
                onValueChangeFinished = {
                    settings =
                        prefs.load()

                    onSettingsChanged()
                },
                valueRange = 10f..22f
            )

            Button(
                onClick = onStart,
                modifier =
                    Modifier.fillMaxWidth(),
                enabled = shizukuReady
            ) {
                Text("Enable ClockOS")
            }

            OutlinedButton(
                onClick = onStop,
                modifier =
                    Modifier.fillMaxWidth(),
                enabled = shizukuReady
            ) {
                Text("Restore Native Clock")
            }
        }
    }

    if (customDateDialog) {
        AlertDialog(
            onDismissRequest = {
                customDateDialog = false
            },
            title = {
                Text("Custom date format")
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Use SimpleDateFormat patterns, " +
                            "for example EEE dd/MM."
                    )

                    TextField(
                        value = customDateInput,
                        onValueChange = {
                            customDateInput = it
                        },
                        singleLine = true,
                        modifier =
                            Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value =
                            customDateInput.trim()

                        if (value.isNotEmpty()) {
                            prefs.set(
                                "dateFormat",
                                "CUSTOM"
                            )
                            prefs.set(
                                "customDateFormat",
                                value
                            )

                            settings =
                                prefs.load()

                            onSettingsChanged()
                        }

                        customDateDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        customDateDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingDropdown(
    label: String,
    selected: String,
    options: List<String>,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedButton(
            onClick = {
                expanded = true
            },
            enabled = enabled,
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalAlignment =
                    Alignment.Start
            ) {
                Text(
                    label,
                    style =
                        MaterialTheme.typography.labelSmall
                )

                Text(selected)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
            }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(option)
                    },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

private fun dateFormatLabel(
    settings: ClockSettings
): String {
    return if (
        settings.dateFormat == "CUSTOM"
    ) {
        settings.customDateFormat
            .ifBlank { "Custom" }
    } else {
        settings.dateFormat
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier =
            Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween,
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(label)

        Switch(
            checked = checked,
            onCheckedChange =
                onCheckedChange
        )
    }
}
