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
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.DisplayCutout
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
    }

    private lateinit var windowManager: WindowManager
    private lateinit var clockView: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var shell: ShizukuShell

    private val handler = Handler(Looper.getMainLooper())

    private var lastColor = Color.WHITE
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastX = Int.MIN_VALUE

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

            clockView = createSystemUiStyledClock()

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
            shell.unbind()
        }

        super.onDestroy()
    }

    private fun createSystemUiStyledClock(): TextView {
        val systemUiContext =
            try {
                createPackageContext(
                    SYSTEM_UI_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY
                )
            } catch (_: Throwable) {
                this
            }

        val view = TextView(systemUiContext)

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
        } else {
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

        view.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO

        return view
    }

    private fun updateClock() {
        if (!::clockView.isInitialized) return

        val settings =
            ClockPrefs(this).load()

        val now = Date()

        val timePattern =
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

        val timeText =
            SimpleDateFormat(
                timePattern,
                Locale.getDefault()
            ).format(now)

        val extras = buildList {
            if (settings.showDate) {
                add(
                    SimpleDateFormat(
                        "dd/MM",
                        Locale.getDefault()
                    ).format(now)
                )
            }

            if (settings.showDay) {
                add(
                    SimpleDateFormat(
                        "EEE",
                        Locale.getDefault()
                    ).format(now)
                )
            }
        }

        applyMainTextSize(settings.sizeSp)

        clockView.text =
            buildClockSpannable(
                timeText = timeText,
                extras = extras,
                availableWidth = nativeClockTextWidthPx()
            )

        clockView.alpha = 1f
        clockView.textScaleX = 1f

        updatePosition(
            clockView,
            clockView.rootWindowInsets
        )
    }

    private fun applyMainTextSize(
        requestedSp: Float
    ) {
        val requested =
            requestedSp.coerceIn(10f, 22f)

        val maxMainWidth =
            nativeClockTextWidthPx()
                .coerceAtLeast(dp(32f))

        var size = requested
        val paint = clockView.paint

        while (size > 8f) {
            paint.textSize = sp(size)

            val timeSample =
                if (ClockPrefs(this).load().format24) {
                    "23:59:59"
                } else {
                    "11:59:59"
                }

            if (
                paint.measureText(timeSample) <=
                    maxMainWidth
            ) {
                break
            }

            size -= 0.5f
        }

        clockView.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            size
        )
    }

    private fun buildClockSpannable(
        timeText: String,
        extras: List<String>,
        availableWidth: Int
    ): CharSequence {
        val builder =
            SpannableStringBuilder(timeText)

        if (extras.isEmpty()) {
            return builder
        }

        val basePaint =
            android.graphics.Paint(clockView.paint).apply {
                textSize = clockView.textSize
            }

        val separator = "  "

        val extraText =
            separator + extras.joinToString(
                separator = "  "
            )

        val mainWidth =
            basePaint.measureText(timeText)

        var scale = 0.70f

        val extraPaint =
            android.graphics.Paint(basePaint)

        while (scale >= 0.35f) {
            extraPaint.textSize =
                clockView.textSize * scale

            val extraWidth =
                extraPaint.measureText(extraText)

            if (
                mainWidth + extraWidth <=
                    availableWidth
            ) {
                break
            }

            scale -= 0.05f
        }

        if (scale < 0.35f) {
            scale = 0.35f
        }

        val start = builder.length

        builder.append(extraText)

        builder.setSpan(
            RelativeSizeSpan(scale),
            start,
            builder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        return builder
    }

    private fun updatePosition(
        view: View,
        insets: WindowInsets?
    ) {
        if (!::params.isInitialized) {
            return
        }

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
                insets.displayCutout?.safeInsetLeft
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

        val parentPadding =
            systemUiPaddingStartPx()

        return maxOf(
            insetLeft,
            cutoutLeft
        ) + parentPadding
    }

    private fun statusBarStartX(): Int =
        statusBarStartX(null)

    private fun statusBarClockTopY(): Int {
        val statusHeight =
            statusBarHeightPx()

        val contentHeight =
            statusBarSystemIconsHeightPx()

        return (
            (statusHeight - contentHeight) / 2
        ).coerceAtLeast(0)
    }

    private fun nativeClockSlotWidthPx(): Int {
        val start =
            systemUiClockPaddingStartPx()

        val end =
            systemUiClockPaddingEndPx()

        val paint =
            android.graphics.Paint(clockViewOrPaint()).apply {
                textSize =
                    systemUiClockSizePx().toFloat()
            }

        val nativeText =
            try {
                val is24 =
                    android.text.format.DateFormat
                        .is24HourFormat(this)

                val pattern =
                    if (is24) {
                        "HH:mm"
                    } else {
                        "hh:mm"
                    }

                SimpleDateFormat(
                    pattern,
                    Locale.getDefault()
                ).format(Date())
            } catch (_: Throwable) {
                "23:59"
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

    private fun nativeClockTextWidthPx(): Int {
        return (
            nativeClockSlotWidthPx() -
                systemUiClockPaddingStartPx() -
                systemUiClockPaddingEndPx()
        ).coerceAtLeast(
            dp(24f)
        )
    }

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
            ?: sp(14f).toInt()

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

    private fun systemUiPaddingStartPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_padding_start"
            )
            ?: dp(0f)

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
                        resources.getDimensionPixelSize(
                            it
                        )
                    }
                ?: dp(24f)
        ).coerceAtLeast(dp(20f))
    }

    private fun systemUiResources():
        SystemUiResources? {
        return try {
            val context =
                createPackageContext(
                    SYSTEM_UI_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY
                )

            SystemUiResources(context)
        } catch (_: Throwable) {
            null
        }
    }

    private fun refreshSystemUiAppearance() {
        if (!::shell.isInitialized) {
            return
        }

        val fallbackNight =
            (
                resources.configuration.uiMode and
                    android.content.res.Configuration
                        .UI_MODE_NIGHT_MASK
            ) ==
                android.content.res.Configuration
                    .UI_MODE_NIGHT_YES

        shell.execute("dumpsys statusbar") { result ->
            val appearanceLine =
                result.lineSequence()
                    .firstOrNull {
                        it.trimStart()
                            .startsWith("mAppearance=")
                    }

            val light =
                appearanceLine?.contains(
                    "LIGHT_STATUS_BARS",
                    ignoreCase = true
                ) ?: !fallbackNight

            val color =
                if (light) {
                    Color.BLACK
                } else {
                    Color.WHITE
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

    private fun sp(value: Float): Float =
        value *
            resources.displayMetrics.scaledDensity

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
