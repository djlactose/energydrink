package com.djlactose.energydrink

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.*
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.isVisible

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
    private lateinit var params: WindowManager.LayoutParams

    // Screen area thresholds
    private var bottomAreaThresholdY: Int = 0
    private var middleAreaLeftBound: Int = 0
    private var middleAreaRightBound: Int = 0

    // For fling/inertia
    private var lastTouchTime = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var velocityX = 0f
    private var velocityY = 0f

    // Handler for fling “animation”
    private val flingHandler = Handler(Looper.getMainLooper())

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

        // Inflate your custom layout (which includes a FrameLayout for the icon container)
        floatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_close, null)

        floatIcon = ImageView(this).apply {
            setImageResource(R.drawable.energy_drink_floating)
            layoutParams = ViewGroup.LayoutParams(200, 200)
        }
        floatingWidget.findViewById<FrameLayout>(R.id.float_icon_container).addView(floatIcon)

        // Window LayoutParams for the floating widget
        params = WindowManager.LayoutParams(
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

        // Set up the "close" area at the bottom
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

        // Handle user touches on the floating icon
        floatIcon.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Stop any ongoing fling when the user touches it again
                        flingHandler.removeCallbacksAndMessages(null)
                        velocityX = 0f
                        velocityY = 0f

                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        lastTouchTime = System.currentTimeMillis()
                        lastX = event.rawX
                        lastY = event.rawY

                        // Save position when pressed down
                        preferences.edit().apply {
                            putInt("floatIconX", initialX)
                            putInt("floatIconY", initialY)
                            apply()
                        }
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Update the position of the icon
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingWidget, params)

                        // Show/hide close area if hovering near bottom center
                        if (params.y > bottomAreaThresholdY &&
                            params.x > middleAreaLeftBound && params.x < middleAreaRightBound
                        ) {
                            closeArea.visibility = View.VISIBLE
                        } else {
                            closeArea.visibility = View.GONE
                        }

                        // Compute velocity in px/ms
                        val currentTime = System.currentTimeMillis()
                        val deltaTime = (currentTime - lastTouchTime).coerceAtLeast(1) // Avoid 0
                        velocityX = (event.rawX - lastX) / deltaTime
                        velocityY = (event.rawY - lastY) / deltaTime

                        lastTouchTime = currentTime
                        lastX = event.rawX
                        lastY = event.rawY

                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // If the close area is visible and the icon intersects it, stop the service
                        if (closeArea.isVisible) {
                            val floatIconRect = Rect()
                            val closeAreaRect = Rect()

                            floatIcon.getGlobalVisibleRect(floatIconRect)
                            closeArea.getGlobalVisibleRect(closeAreaRect)

                            if (Rect.intersects(floatIconRect, closeAreaRect)) {
                                stopSelf()
                                return true
                            }
                        }
                        closeArea.visibility = View.GONE

                        // Initiate a fling animation if the velocity is large enough
                        startFlingAnimation(velocityX, velocityY)

                        return true
                    }
                }
                return false
            }
        })
    }

    /**
     * Start a simple fling animation using the last known velocity.
     * The icon continues moving after the user lifts their finger,
     * gradually slowing down due to friction until it stops.
     */
    private fun startFlingAnimation(vX: Float, vY: Float) {
        var localVx = vX * 2f
        var localVy = vY * 2f

        // Create a runnable that updates position at ~60 FPS
        val flingRunnable = object : Runnable {
            override fun run() {
                // Simulate friction
                localVx *= 0.98f
                localVy *= 0.98f

                // Update position
                params.x += localVx.toInt()
                params.y += localVy.toInt()

                // Clamp or bounce off edges if desired, e.g.:
                // clampXAndYToScreen() // or bounce if hitting edges

                // Update the floating widget
                windowManager.updateViewLayout(floatingWidget, params)

                // Continue until velocity is small
                if (kotlin.math.abs(localVx) > 0.5f || kotlin.math.abs(localVy) > 0.5f) {
                    flingHandler.postDelayed(this, 16)
                }
            }
        }

        // Start the fling
        flingHandler.post(flingRunnable)
    }

    /** Optionally clamp X and Y so the icon stays on screen. */
    private fun clampXAndYToScreen() {
        val displayMetrics = resources.displayMetrics
        val maxX = displayMetrics.widthPixels - floatingWidget.width
        val maxY = displayMetrics.heightPixels - floatingWidget.height

        if (params.x < 0) params.x = 0
        if (params.x > maxX) params.x = maxX
        if (params.y < 0) params.y = 0
        if (params.y > maxY) params.y = maxY
    }

    /**
     * Recalculate any screen-size dependent thresholds
     * (i.e. if orientation changes, etc.).
     */
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
