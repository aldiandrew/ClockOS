package com.aldiandrew.clockos

import android.content.Context

data class ClockSettings(
    val format24: Boolean = true,
    val showSeconds: Boolean = false,
    val showDate: Boolean = false,
    val showDay: Boolean = false,
    val sizeSp: Float = 14f,
    val weight: Int = 500
)

class ClockPrefs(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            "clockos",
            Context.MODE_PRIVATE
        )

    fun load() = ClockSettings(
        format24 = prefs.getBoolean("format24", true),
        showSeconds = prefs.getBoolean("showSeconds", false),
        showDate = prefs.getBoolean("showDate", false),
        showDay = prefs.getBoolean("showDay", false),
        sizeSp = prefs.getFloat("sizeSp", 14f)
            .coerceIn(10f, 22f),
        weight = prefs.getInt("weight", 500)
    )

    fun set(
        key: String,
        value: Any
    ) {
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
