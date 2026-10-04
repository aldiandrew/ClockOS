package com.aldiandrew.clockos

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.*
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class ClockOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var clockView: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
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

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(WindowManager::class.java)
        val view = TextView(this).apply {
            setSingleLine(true)
            setTextColor(Color.WHITE)
            includeFontPadding = false
            gravity = Gravity.CENTER
            elevation = 0f
        }
        clockView = view

        val prefs = ClockPrefs(this).load()
        view.textSize = prefs.sizeSp
        view.setTypeface(android.graphics.Typeface.DEFAULT, prefs.weight)
        view.setTextColor(
            when (prefs.colorMode) {
                "black" -> Color.BLACK
                else -> Color.WHITE
            }
        )

        val metrics = resources.displayMetrics
        val statusBarHeight = resources.getIdentifier("status_bar_height", "dimen", "android")
            .takeIf { it != 0 }
            ?.let { resources.getDimensionPixelSize(it) }
            ?: (24 * metrics.density).toInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            statusBarHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = when (prefs.position) {
                "left" -> Gravity.TOP or Gravity.LEFT
                "right" -> Gravity.TOP or Gravity.RIGHT
                else -> Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }
            x = if (prefs.position == "left") 8 else if (prefs.position == "right") 8 else 0
            y = 0
        }

        try {
            windowManager.addView(view, params)
        } catch (_: Throwable) {
            stopSelf()
            return
        }

        handler.post(tick)
    }

    private fun updateClock() {
        val prefs = ClockPrefs(this).load()
        val pattern = buildString {
            append(if (prefs.format24) "HH:mm" else "hh:mm a")
            if (prefs.showSeconds) append(":ss")
            if (prefs.showDate) append(" dd/MM")
            if (prefs.showDay) append(" EEE")
        }
        clockView?.text = SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "clockos",
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.clock_running)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        clockView?.let {
            try { windowManager.removeView(it) } catch (_: Throwable) {}
        }
        clockView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}