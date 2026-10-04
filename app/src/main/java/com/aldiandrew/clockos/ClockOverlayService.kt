package com.aldiandrew.clockos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockOverlayService : Service() {

    companion object {
        const val ACTION_SETTINGS_CHANGED =
            "com.aldiandrew.clockos.ACTION_SETTINGS_CHANGED"

        private const val CHANNEL_ID = "clockos"
        private const val CHANNEL_NAME = "ClockOS"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        // android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        private const val APPEARANCE_LIGHT_STATUS_BARS = 8L

        private const val EXTRA_RELATIVE_SIZE = 0.70f
    }

    private lateinit var windowManager: WindowManager
    private lateinit var clockView: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var shell: ShizukuShell
    private lateinit var systemUiContext: Context

    private val handler = Handler(Looper.getMainLooper())

    private var lastColor = Int.MIN_VALUE
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE

    private var lastRendered = ""
    private var lastSizeSp = Float.NaN

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000L)
        }
    }

    private val appearanceTick = object : Runnable {
        override fun run() {
            refreshSystemUiAppearance()
            handler.postDelayed(this, 3000L)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (intent?.action == ACTION_SETTINGS_CHANGED) {
            lastRendered = ""
            updateClock()
            refreshSystemUiAppearance()
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        if (
            Build.VERSION.SDK_INT >= 23 &&
            !Settings.canDrawOverlays(this)
        ) {
            stopSelf()
            return
        }

        try {
            shell = ShizukuShell(this)

            // Hide the real SystemUI clock before the overlay is shown.
            // This is the only stock Android command available to remove
            // the native clock without root/Xposed.
            shell.execute(
                "cmd statusbar send-disable-flag clock"
            ) { }

            systemUiContext =
                try {
                    createPackageContext(
                        SYSTEM_UI_PACKAGE,
                        Context.CONTEXT_IGNORE_SECURITY
                    )
                } catch (_: Throwable) {
                    this
                }

            createNotificationChannel()

            startForeground(
                1001,
                Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(
                        android.R.drawable.ic_menu_recent_history
                    )
                    .setContentTitle("ClockOS")
                    .setContentText("Custom clock is active")
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
            )

            windowManager =
                getSystemService(WindowManager::class.java)

            clockView =
                createSystemUiStyledClock()

            params = WindowManager.LayoutParams(
                nativeClockSlotWidthPx(),
                statusBarSystemIconsHeightPx(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = statusBarStartX()
                y = statusBarClockTopY()

                if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams
                            .LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }

                if (Build.VERSION.SDK_INT >= 30) {
                    setFitInsetsTypes(0)
                }
            }

            windowManager.addView(
                clockView,
                params
            )

            clockView.setOnApplyWindowInsetsListener { view, insets ->
                updatePosition(view, insets)
                insets
            }

            clockView.post {
                updatePosition(
                    clockView,
                    clockView.rootWindowInsets
                )
            }

            updateClock()
            refreshSystemUiAppearance()

            handler.post(tick)
            handler.postDelayed(
                appearanceTick,
                500L
            )
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    override fun onBind(
        intent: Intent?
    ): android.os.IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        try {
            if (
                ::windowManager.isInitialized &&
                ::clockView.isInitialized
            ) {
                windowManager.removeViewImmediate(
                    clockView
                )
            }
        } catch (_: Throwable) {
        }

        if (::shell.isInitialized) {
            // Restore the native SystemUI clock when ClockOS stops.
            shell.execute(
                "cmd statusbar send-disable-flag none"
            ) {
                shell.unbind()
            }
        }

        super.onDestroy()
    }

    private fun createSystemUiStyledClock(): TextView {
        val view =
            TextView(systemUiContext)

        val styleId =
            systemUiContext.resources.getIdentifier(
                "TextAppearance.StatusBar.Clock",
                "style",
                SYSTEM_UI_PACKAGE
            )

        if (styleId != 0) {
            try {
                view.setTextAppearance(styleId)
            } catch (_: Throwable) {
            }
        }

        if (view.typeface == null) {
            view.setTypeface(
                Typeface.create(
                    "sans-serif-medium",
                    Typeface.NORMAL
                )
            )
        }

        view.setSingleLine(true)
        view.includeFontPadding = false
        view.gravity =
            Gravity.CENTER_VERTICAL or Gravity.START

        view.setPadding(
            systemUiClockPaddingStartPx(),
            0,
            systemUiClockPaddingEndPx(),
            0
        )

        // Transparent window: never paint a fake/black status-bar background.
        // The custom text must look like native SystemUI over whatever
        // background the device is currently using.
        view.background = null
        view.setTextColor(Color.WHITE)

        view.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO

        return view
    }

    private fun updateClock() {
        if (!::clockView.isInitialized) return

        val settings =
            ClockPrefs(this).load()

        if (settings.sizeSp != lastSizeSp) {
            // TextView.setTextSize(float) is SP, while getTextSize() is PX.
            // Use the explicit unit API to avoid the previous giant-clock bug.
            clockView.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                settings.sizeSp
            )
            lastSizeSp = settings.sizeSp
        }

        val now = Date()
        val timePattern =
            buildTimePattern(settings)

        val timeText =
            SimpleDateFormat(
                timePattern,
                Locale.getDefault()
            ).format(now)

        val extras =
            buildList {
                if (settings.showDate) {
                    add(
                        formatDate(
                            settings,
                            now
                        )
                    )
                }

            }

        val rendered =
            buildClockSpannable(
                timeText = timeText,
                extras = extras,
                amPmStyle = settings.amPmStyle
            )

        val renderedKey =
            rendered.toString() +
                "|" +
                settings.amPmStyle +
                "|" +
                settings.dateStyle

        if (renderedKey != lastRendered) {
            clockView.text =
                rendered
            lastRendered = renderedKey

            // Keep the requested font size and only compress the complete
            // line horizontally when the native clock slot is too narrow.
            applyHorizontalFit()
        }

        updatePosition(
            clockView,
            clockView.rootWindowInsets
        )
    }

    private fun buildTimePattern(
        settings: ClockSettings
    ): String {
        val clock =
            if (settings.format24) {
                if (settings.showSeconds) {
                    "HH:mm:ss"
                } else {
                    "HH:mm"
                }
            } else {
                if (settings.showSeconds) {
                    "hh:mm:ss"
                } else {
                    "hh:mm"
                }
            }

        return if (
            !settings.format24 &&
            settings.amPmStyle != 2
        ) {
            "$clock a"
        } else {
            clock
        }
    }

    private fun formatDate(
        settings: ClockSettings,
        now: Date
    ): String {
        val pattern =
            if (
                settings.dateFormat == "CUSTOM"
            ) {
                settings.customDateFormat
                    .takeIf { it.isNotBlank() }
                    ?: "dd/MM"
            } else {
                settings.dateFormat
            }

        val formatted =
            try {
                SimpleDateFormat(
                    pattern,
                    Locale.getDefault()
                ).format(now)
            } catch (_: IllegalArgumentException) {
                SimpleDateFormat(
                    "dd/MM",
                    Locale.getDefault()
                ).format(now)
            }

        return when (settings.dateStyle) {
            1 -> formatted.lowercase(
                Locale.getDefault()
            )

            2 -> formatted.uppercase(
                Locale.getDefault()
            )

            else -> formatted
        }
    }

    private fun buildClockSpannable(
        timeText: String,
        extras: List<String>,
        amPmStyle: Int
    ): CharSequence {
        val builder =
            SpannableStringBuilder(timeText)

        if (amPmStyle != 2) {
            val amPmStart =
                timeText.lastIndexOf(' ') + 1

            if (
                amPmStyle == 1 &&
                amPmStart in 1 until builder.length
            ) {
                builder.setSpan(
                    RelativeSizeSpan(
                        EXTRA_RELATIVE_SIZE
                    ),
                    amPmStart,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        if (extras.isNotEmpty()) {
            val start =
                builder.length

            builder.append(
                "  " +
                    extras.joinToString(
                        separator = "  "
                    )
            )

            builder.setSpan(
                RelativeSizeSpan(
                    EXTRA_RELATIVE_SIZE
                ),
                start,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return builder
    }

    private fun applyHorizontalFit() {
        // Keep the main clock size fixed exactly as selected by the user.
        // Do not use textScaleX: Sakura keeps the main time font size stable
        // and changes the relative size of secondary date/AM-PM content.
        if (!::clockView.isInitialized) return
        clockView.textScaleX = 1f
    }

    private fun updatePosition(
        view: View,
        insets: WindowInsets?
    ) {
        if (!::params.isInitialized) return

        val targetX =
            statusBarStartX(insets)

        val targetY =
            statusBarClockTopY()

        val targetWidth =
            nativeClockSlotWidthPx()

        val targetHeight =
            statusBarSystemIconsHeightPx()

        if (
            targetX == lastX &&
            targetY == lastY &&
            targetWidth == lastWidth &&
            targetHeight == lastHeight
        ) {
            return
        }

        params.x = targetX
        params.y = targetY
        params.width = targetWidth
        params.height = targetHeight

        try {
            windowManager.updateViewLayout(
                view,
                params
            )

            lastX = targetX
            lastY = targetY
            lastWidth = targetWidth
            lastHeight = targetHeight
        } catch (_: Throwable) {
        }
    }

    private fun statusBarStartX(
        insets: WindowInsets? =
            clockViewOrNull()?.rootWindowInsets
    ): Int {
        var insetLeft = 0
        var cutoutLeft = 0

        if (
            insets != null &&
            Build.VERSION.SDK_INT >= 30
        ) {
            val bars =
                insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars()
                )

            insetLeft = bars.left
            cutoutLeft =
                insets.displayCutout
                    ?.safeInsetLeft
                    ?: 0
        } else if (
            insets != null &&
            Build.VERSION.SDK_INT >= 28
        ) {
            @Suppress("DEPRECATION")
            insetLeft =
                insets.systemWindowInsetLeft

            cutoutLeft =
                insets.displayCutout
                    ?.safeInsetLeft
                    ?: 0
        }

        return maxOf(
            insetLeft,
            cutoutLeft
        ) + systemUiPaddingStartPx()
    }

    private fun statusBarStartX(): Int =
        statusBarStartX(null)

    private fun statusBarClockTopY(): Int =
        systemUiPaddingTopPx()

    private fun nativeClockSlotWidthPx(): Int {
        val start =
            systemUiClockPaddingStartPx()

        val end =
            systemUiClockPaddingEndPx()

        val paint =
            android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
            ).apply {
                typeface =
                    clockViewOrPaint().typeface
                textSize =
                    systemUiClockSizePx().toFloat()
                textScaleX = 1f
            }

        val nativeText =
            if (
                DateFormat.is24HourFormat(
                    this
                )
            ) {
                "23:59"
            } else {
                "11:59"
            }

        val measured =
            paint.measureText(
                nativeText
            ).toInt()

        return (
            start +
                measured +
                end
        ).coerceAtLeast(
            dp(40f)
        )
    }

    private fun nativeClockTextWidthPx(): Int =
        (
            nativeClockSlotWidthPx() -
                systemUiClockPaddingStartPx() -
                systemUiClockPaddingEndPx()
        ).coerceAtLeast(
            dp(20f)
        )

    private fun clockViewOrPaint():
        android.graphics.Paint {
        return if (::clockView.isInitialized) {
            clockView.paint
        } else {
            android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
            ).apply {
                typeface =
                    Typeface.create(
                        "sans-serif-medium",
                        Typeface.NORMAL
                    )
            }
        }
    }

    private fun systemUiClockSizePx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_clock_size"
            )
            ?: dp(14f)

    private fun systemUiClockPaddingStartPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_left_clock_starting_padding"
            )
            ?: 0

    private fun systemUiClockPaddingEndPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_left_clock_end_padding"
            )
            ?: dp(2f)

    private fun systemUiPaddingStartPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_padding_start"
            )
            ?: 0

    private fun systemUiPaddingTopPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_padding_top"
            )
            ?: 0

    private fun statusBarSystemIconsHeightPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_system_icons_height"
            )
            ?: statusBarHeightPx()

    private fun statusBarHeightPx(): Int {
        val systemUi =
            systemUiResources()

        return (
            systemUi?.getDimensionPixelSizeByName(
                "status_bar_height"
            )
                ?: resources.getIdentifier(
                    "status_bar_height",
                    "dimen",
                    "android"
                )
                    .takeIf { it != 0 }
                    ?.let {
                        resources.getDimensionPixelSize(it)
                    }
                ?: dp(24f)
        ).coerceAtLeast(
            dp(20f)
        )
    }

    private fun systemUiResources():
        SystemUiResources? {
        return try {
            SystemUiResources(
                systemUiContext
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun refreshSystemUiAppearance() {
        if (!::shell.isInitialized) return

        val fallbackNight =
            (
                resources.configuration.uiMode and
                    android.content.res.Configuration
                        .UI_MODE_NIGHT_MASK
            ) ==
                android.content.res.Configuration
                    .UI_MODE_NIGHT_YES

        shell.execute(
            "dumpsys statusbar"
        ) { result ->
            val appearance =
                parseStatusBarAppearance(
                    result
                )

            val color =
                when (appearance) {
                    true -> Color.BLACK
                    false -> Color.WHITE
                    null -> nativeClockColor()
                        ?: if (fallbackNight) {
                            Color.WHITE
                        } else {
                            Color.BLACK
                        }
                }

            handler.post {
                if (
                    ::clockView.isInitialized &&
                    color != lastColor
                ) {
                    lastColor = color
                    clockView.setTextColor(color)
                }
            }
        }
    }

    private fun parseStatusBarAppearance(
        dump: String
    ): Boolean? {
        val line =
            dump.lineSequence()
                .firstOrNull {
                    it.trimStart()
                        .startsWith("mAppearance=")
                }
                ?: return null

        val valueText =
            line.substringAfter('=')
                .trim()
                .takeWhile {
                    it.isDigit() ||
                        it in "abcdefABCDEFxX"
                }

        if (valueText.isBlank()) {
            if (
                line.contains(
                    "LIGHT_STATUS_BARS",
                    true
                )
            ) {
                return true
            }

            return null
        }

        val value =
            try {
                if (
                    valueText.startsWith(
                        "0x",
                        true
                    )
                ) {
                    valueText
                        .substring(2)
                        .toLongOrNull(16)
                } else {
                    valueText.toLongOrNull()
                }
            } catch (_: Throwable) {
                null
            }

        return value?.let {
            (it and APPEARANCE_LIGHT_STATUS_BARS) != 0L
        }
    }

    private fun nativeClockColor(): Int? {
        val resources =
            systemUiContext.resources

        val id =
            resources.getIdentifier(
                "status_bar_clock_color",
                "color",
                SYSTEM_UI_PACKAGE
            )

        if (id == 0) return null

        return try {
            resources.getColor(
                id,
                systemUiContext.theme
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun clockViewOrNull(): TextView? =
        if (::clockView.isInitialized) {
            clockView
        } else {
            null
        }

    private fun dp(value: Float): Int =
        (
            value *
                resources.displayMetrics.density
        )
            .toInt()
            .coerceAtLeast(1)

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
        )
    }

    private class SystemUiResources(
        private val context: Context
    ) {
        private val resources =
            context.resources

        fun getDimensionPixelSizeByName(
            name: String
        ): Int? {
            val id =
                resources.getIdentifier(
                    name,
                    "dimen",
                    SYSTEM_UI_PACKAGE
                )

            return if (id != 0) {
                resources.getDimensionPixelSize(id)
            } else {
                null
            }
        }
    }
}
