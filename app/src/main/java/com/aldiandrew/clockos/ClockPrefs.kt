package com.aldiandrew.clockos

import android.content.Context

data class ClockSettings(
    val format24: Boolean = true,
    val showDate: Boolean = false,
    val dateFormat: String = "dd/MM",
    val customDateFormat: String = "",
    val dateStyle: Int = 0,
    val amPmStyle: Int = 2,
    val sizeSp: Float = 14f,
    val horizontalPositionDp: Float = 0f,
    val verticalPositionDp: Float = 0f
)

class ClockPrefs(context: Context) {
    private val prefs =
        context.getSharedPreferences(
            "clockos",
            Context.MODE_PRIVATE
        )

    fun load() = ClockSettings(
        format24 = prefs.getBoolean("format24", true),
        showDate = prefs.getBoolean("showDate", false),
        dateFormat = prefs.getString("dateFormat", "dd/MM")
            ?: "dd/MM",
        customDateFormat = prefs.getString("customDateFormat", "")
            ?: "",
        dateStyle = prefs.getInt("dateStyle", 0)
            .coerceIn(0, 2),
        amPmStyle = prefs.getInt("amPmStyle", 2)
            .coerceIn(0, 2),
        sizeSp = prefs.getFloat("sizeSp", 14f)
            .coerceIn(10f, 22f),
        horizontalPositionDp = prefs.getFloat(
            "horizontalPositionDp",
            0f
        ).coerceIn(-100f, 100f),
        verticalPositionDp = prefs.getFloat(
            "verticalPositionDp",
            0f
        ).coerceIn(-20f, 20f)
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
