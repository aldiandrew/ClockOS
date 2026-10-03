package com.aldiandrew.clockos

import android.content.Context

data class ClockSettings(
    val format24: Boolean = true,
    val showSeconds: Boolean = false,
    val showDate: Boolean = false,
    val showDay: Boolean = false,
    val sizeSp: Float = 14f,
    val position: String = "center",
    val colorMode: String = "system",
    val customColor: Int = 0xFFFFFFFF.toInt(),
    val weight: Int = 400
)

class ClockPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("clockos", Context.MODE_PRIVATE)

    fun load() = ClockSettings(
        format24 = prefs.getBoolean("format24", true),
        showSeconds = prefs.getBoolean("showSeconds", false),
        showDate = prefs.getBoolean("showDate", false),
        showDay = prefs.getBoolean("showDay", false),
        sizeSp = prefs.getFloat("sizeSp", 14f),
        position = prefs.getString("position", "center") ?: "center",
        colorMode = prefs.getString("colorMode", "system") ?: "system",
        customColor = prefs.getInt("customColor", 0xFFFFFFFF.toInt()),
        weight = prefs.getInt("weight", 400)
    )

    fun set(key: String, value: Any) {
        prefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
        }.apply()
    }
}
