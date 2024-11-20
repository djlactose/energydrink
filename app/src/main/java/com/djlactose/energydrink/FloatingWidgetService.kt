package com.djlactose.energydrink

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.IBinder
import android.os.PowerManager
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView

class FloatingWidgetService : Service() {

    companion object {
        var isServiceRunning = false
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingWidget: View
    private lateinit var floatIcon: ImageView
    private lateinit var closeArea: FrameLayout
    private lateinit var closeIcon: ImageView
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var preferences: SharedPreferences

    private var bottomAreaThresholdY: Int = 0
    private var middleAreaLeftBound: Int = 0
    private var middleAreaRightBound: Int = 0

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true

        preferences = getSharedPreferences("FloatingWidgetPrefs", Context.MODE_PRIVATE)
        val savedX = preferences.getInt("floatIconX", 0)
        val savedY = preferences.getInt("floatIconY", 0)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "EnergyDrink:WakeLock"
        )
        wakeLock.acquire()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_close, null)

        floatIcon = ImageView(this).apply {
            setImageResource(R.drawable.energy_drink_floating)
            layoutParams = ViewGroup.LayoutParams(200, 200)
        }
        floatingWidget.findViewById<FrameLayout>(R.id.float_icon_container).addView(floatIcon)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = savedX
            y = savedY
        }

        windowManager.addView(floatingWidget, params)

        closeArea = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.rounded_close_area)
            visibility = View.GONE

            closeIcon = ImageView(context).apply {
                setImageResource(R.drawable.ic_close)
            }

            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            addView(closeIcon, layoutParams)
        }

        val closeParams = WindowManager.LayoutParams(
            450,
            150,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        windowManager.addView(closeArea, closeParams)

        updateDimensions()

        floatIcon.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        // Save position when pressed down
                        preferences.edit().apply {
                            putInt("floatIconX", initialX)
                            putInt("floatIconY", initialY)
                            apply()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingWidget, params)

                        if (params.y > bottomAreaThresholdY &&
                            params.x > middleAreaLeftBound && params.x < middleAreaRightBound
                        ) {
                            closeArea.visibility = View.VISIBLE
                        } else {
                            closeArea.visibility = View.GONE
                        }

                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (closeArea.visibility == View.VISIBLE) {
                            val floatIconRect = Rect()
                            val closeAreaRect = Rect()

                            floatIcon.getGlobalVisibleRect(floatIconRect)
                            closeArea.getGlobalVisibleRect(closeAreaRect)

                            if (Rect.intersects(floatIconRect, closeAreaRect)) {
                                stopSelf()
                            }
                        }
                        closeArea.visibility = View.GONE
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun updateDimensions() {
        val displayMetrics = resources.displayMetrics
        bottomAreaThresholdY = displayMetrics.heightPixels * 4 / 5
        middleAreaLeftBound = (displayMetrics.widthPixels / 2) - 225
        middleAreaRightBound = (displayMetrics.widthPixels / 2) + 225
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateDimensions()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        if (this::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        windowManager.removeView(floatingWidget)
        windowManager.removeView(closeArea)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
