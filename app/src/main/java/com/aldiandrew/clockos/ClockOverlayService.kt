package com.aldiandrew.clockos

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class ClockOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var clockView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()

        try {
            createNotificationChannel()
            startForeground(
                1001,
                Notification.Builder(this, "clockos")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("ClockOS")
                    .setContentText(getString(R.string.clock_running))
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
            )

            windowManager = getSystemService(WindowManager::class.java)

            val view = TextView(this).apply {
                setSingleLine(true)
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL
                elevation = 0f
            }
            clockView = view

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                statusBarHeightPx(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                x = dp(8f)
                y = 0

                if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                if (Build.VERSION.SDK_INT >= 30) {
                    setFitInsetsTypes(0)
                }
            }

            layoutParams = params
            windowManager.addView(view, params)

            view.setOnApplyWindowInsetsListener { v, insets ->
                updateLayout()
                insets
            }

            view.post { updateLayout() }
            updateClock()
            handler.post(tick)
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    private fun updateClock() {
        val prefs = ClockPrefs(this).load()

        val pattern = buildString {
            append(if (prefs.format24) "HH:mm" else "hh:mm a")
            if (prefs.showSeconds) append(":ss")
            if (prefs.showDate) append(" dd/MM")
            if (prefs.showDay) append(" EEE")
        }

        clockView?.apply {
            textSize = prefs.sizeSp
            setTypeface(android.graphics.Typeface.DEFAULT, prefs.weight)
            setTextColor(resolveColor(prefs.colorMode))
            text = SimpleDateFormat(
                pattern,
                Locale.getDefault()
            ).format(Date())
        }

        updateLayout()
    }

    private fun resolveColor(mode: String): Int {
        return when (mode) {
            "black" -> Color.BLACK
            "white" -> Color.WHITE
            else -> {
                // SystemUI's light status-bar appearance uses dark icons/text.
                // Read the current UI mode as a reliable fallback for the overlay.
                val night =
                    (resources.configuration.uiMode and
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                if (night) Color.WHITE else Color.BLACK
            }
        }
    }

    private fun updateLayout() {
        val view = clockView ?: return
        val params = layoutParams ?: return

        val prefs = ClockPrefs(this).load()

        params.gravity = when (prefs.position) {
            "left" -> Gravity.TOP or Gravity.LEFT
            "right" -> Gravity.TOP or Gravity.RIGHT
            else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
        }

        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = statusBarHeightPx()

        val horizontalMargin = dp(8f)
        params.x = when (prefs.position) {
            "left" -> horizontalMargin
            "right" -> horizontalMargin
            "center" -> 0
            else -> horizontalMargin
        }

        // Android status-bar windows are vertically centered inside the status-bar band.
        // The punch-hole is in the center, so left mode remains in the unobstructed area.
        val topInset = statusBarTopInset(view)
        val contentHeight = view.measuredHeight.coerceAtLeast(dp(1f))
        params.y = ((topInset - contentHeight) / 2).coerceAtLeast(0)

        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: Throwable) {
        }
    }

    private fun statusBarTopInset(view: View): Int {
        return if (Build.VERSION.SDK_INT >= 30) {
            view.rootWindowInsets
                ?.getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.statusBars()
                )
                ?.top
                ?.coerceAtLeast(statusBarHeightPx())
                ?: statusBarHeightPx()
        } else {
            view.rootWindowInsets?.systemWindowInsetTop
                ?.coerceAtLeast(statusBarHeightPx())
                ?: statusBarHeightPx()
        }
    }

    private fun statusBarHeightPx(): Int {
        val id = resources.getIdentifier(
            "status_bar_height",
            "dimen",
            "android"
        )
        return if (id != 0) {
            resources.getDimensionPixelSize(id)
        } else {
            dp(24f)
        }
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "clockos",
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.clock_running)
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        clockView?.let {
            try {
                windowManager.removeViewImmediate(it)
            } catch (_: Throwable) {
            }
        }

        clockView = null
        layoutParams = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
