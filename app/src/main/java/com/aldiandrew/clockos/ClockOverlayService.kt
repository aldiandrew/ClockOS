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
import android.view.DisplayCutout
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

class ClockOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var clockView: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var shell: ShizukuShell

    private val handler = Handler(Looper.getMainLooper())
    private var lastColor: Int = Color.WHITE
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastX = Int.MIN_VALUE

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(
                this,
                if (ClockPrefs(this@ClockOverlayService).load().showSeconds) {
                    1000L
                } else {
                    1000L
                }
            )
        }
    }

    private val appearanceTick = object : Runnable {
        override fun run() {
            refreshSystemUiAppearance()
            handler.postDelayed(this, 3000L)
        }
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        try {
            shell = ShizukuShell(this)
            createNotificationChannel()
            startForeground(
                1001,
                Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                    .setContentTitle("ClockOS")
                    .setContentText("Custom clock is active")
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
            )

            windowManager = getSystemService(WindowManager::class.java)

            clockView = TextView(this).apply {
                setSingleLine(true)
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL))
                importantForAccessibility =
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            params = WindowManager.LayoutParams(
                clockAreaWidthPx(),
                statusBarHeightPx(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0

                if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }

                if (Build.VERSION.SDK_INT >= 30) {
                    setFitInsetsTypes(0)
                }
            }

            windowManager.addView(clockView, params)

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
            handler.postDelayed(appearanceTick, 500L)
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        try {
            if (::windowManager.isInitialized && ::clockView.isInitialized) {
                windowManager.removeViewImmediate(clockView)
            }
        } catch (_: Throwable) {
        }

        if (::shell.isInitialized) {
            shell.unbind()
        }

        super.onDestroy()
    }

    private fun updateClock() {
        if (!::clockView.isInitialized) return

        val settings = ClockPrefs(this).load()
        val pattern = buildString {
            append(if (settings.format24) "HH:mm" else "hh:mm a")

            if (settings.showSeconds) {
                append(":ss")
            }

            if (settings.showDate) {
                append(" dd/MM")
            }

            if (settings.showDay) {
                append(" EEE")
            }
        }

        val text = SimpleDateFormat(
            pattern,
            Locale.getDefault()
        ).format(Date())

        clockView.text = text

        applyTextSizing(settings.sizeSp)
        updatePosition(
            clockView,
            clockView.rootWindowInsets
        )
    }

    private fun applyTextSizing(requestedSp: Float) {
        val maxWidth = (
            clockAreaWidthPx() -
                systemUiClockPaddingStartPx() -
                systemUiClockPaddingEndPx() -
                dp(2f)
        ).coerceAtLeast(dp(24f))

        var size = requestedSp.coerceIn(10f, 22f)
        val metrics = clockView.paint

        while (size > 8f) {
            clockView.textSize = size
            if (metrics.measureText(clockView.text.toString()) <= maxWidth) {
                break
            }
            size -= 0.5f
        }

        val measured = metrics.measureText(clockView.text.toString())
        clockView.textScaleX =
            if (measured > 0f && measured > maxWidth) {
                maxWidth / measured
            } else {
                1f
            }

        clockView.setPadding(
            systemUiClockPaddingStartPx(),
            0,
            systemUiClockPaddingEndPx(),
            0
        )
    }

    private fun updatePosition(
        view: View,
        insets: WindowInsets?
    ) {
        if (!::params.isInitialized) return

        val leftInset: Int
        val cutout: DisplayCutout?

        if (insets != null && Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars()
            )
            leftInset = bars.left
            cutout = insets.displayCutout
        } else if (insets != null && Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            leftInset = insets.systemWindowInsetLeft
            cutout = insets.displayCutout
        } else {
            leftInset = 0
            cutout = null
        }

        val cutoutLeft =
            cutout?.safeInsetLeft ?: 0

        val targetX = maxOf(
            leftInset,
            cutoutLeft
        ).coerceAtLeast(0)

        val targetWidth = clockAreaWidthPx()
        val targetHeight = statusBarHeightPx()

        if (
            targetX == lastX &&
            targetWidth == lastWidth &&
            targetHeight == lastHeight
        ) {
            return
        }

        params.x = targetX
        params.y = 0
        params.width = targetWidth
        params.height = targetHeight

        try {
            windowManager.updateViewLayout(
                view,
                params
            )

            lastX = targetX
            lastWidth = targetWidth
            lastHeight = targetHeight
        } catch (_: Throwable) {
        }
    }

    private fun clockAreaWidthPx(): Int {
        val systemUi = systemUiResources()

        val clockSizePx =
            systemUi?.getDimensionPixelSizeByName(
                "status_bar_clock_size"
            ) ?: sp(14f).toInt()

        val startPadding =
            systemUi?.getDimensionPixelSizeByName(
                "status_bar_left_clock_starting_padding"
            ) ?: dp(0f)

        val endPadding =
            systemUi?.getDimensionPixelSizeByName(
                "status_bar_left_clock_end_padding"
            ) ?: dp(2f)

        val paint = clockViewOrPaint()
        paint.textSize = clockSizePx.toFloat()

        val is24 =
            Settings.System.getString(
                contentResolver,
                Settings.System.TIME_12_24
            )?.equals("24", ignoreCase = true) != false

        val sample =
            if (is24) "23:59" else "11:59 PM"

        val measured =
            paint.measureText(sample).toInt()

        return (
            startPadding +
                measured +
                endPadding +
                dp(2f)
            ).coerceAtLeast(dp(40f))
            .coerceAtMost(dp(88f))
    }

    private fun clockViewOrPaint(): android.graphics.Paint {
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

    private fun systemUiClockPaddingStartPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_left_clock_starting_padding"
            )
            ?: dp(0f)

    private fun systemUiClockPaddingEndPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_left_clock_end_padding"
            )
            ?: dp(2f)

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
                ).takeIf { it != 0 }
                    ?.let { resources.getDimensionPixelSize(it) }
                ?: dp(24f)
        ).coerceAtLeast(dp(20f))
    }

    private fun systemUiResources(): SystemUiResources? {
        return try {
            val context = createPackageContext(
                SYSTEM_UI_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY
            )
            SystemUiResources(context)
        } catch (_: Throwable) {
            null
        }
    }

    private fun refreshSystemUiAppearance() {
        if (!::shell.isInitialized) return

        val fallbackNight =
            (
                resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK
            ) == android.content.res.Configuration.UI_MODE_NIGHT_YES

        val fallback =
            if (fallbackNight) Color.WHITE else Color.BLACK

        shell.execute("dumpsys statusbar") { result ->
            val appearanceLine =
                result.lineSequence()
                    .firstOrNull {
                        it.trimStart().startsWith("mAppearance=")
                    }

            val light =
                appearanceLine?.contains(
                    "LIGHT_STATUS_BARS",
                    ignoreCase = true
                ) ?: !fallbackNight

            val color =
                if (light) Color.BLACK else Color.WHITE

            handler.post {
                if (::clockView.isInitialized && color != lastColor) {
                    lastColor = color
                    clockView.setTextColor(color)
                }
            }
        }
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(1)

    private fun sp(value: Float): Float =
        value * resources.displayMetrics.scaledDensity

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        val manager =
            getSystemService(NotificationManager::class.java)

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
        private val resources = context.resources

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

    private companion object {
        const val CHANNEL_ID = "clockos"
        const val CHANNEL_NAME = "ClockOS"
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
