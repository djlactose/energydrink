package com.djlactose.energydrink

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences // Add this import
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.PowerManager
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView

class FloatingWidgetService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var floatingWidget: View
    private lateinit var floatIcon: ImageView
    private lateinit var closeArea: FrameLayout
    private lateinit var closeIcon: ImageView
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var sharedPreferences: SharedPreferences // Declare SharedPreferences

    // Declare the variables for screen boundaries used in landscape and portrait modes
    private var bottomAreaThresholdY: Int = 0
    private var middleAreaLeftBound: Int = 0
    private var middleAreaRightBound: Int = 0

    override fun onCreate() {
        super.onCreate()

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("FloatingWidgetPrefs", Context.MODE_PRIVATE)

        // Acquire the wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "EnergyDrink:WakeLock")
        wakeLock.acquire()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Initialize the floating widget layout and set the new floating icon image
        floatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_close, null)

        // Initialize the floating icon with specified size
        floatIcon = ImageView(this).apply {
            setImageResource(R.drawable.energy_drink_floating)

            // Set layout parameters for size 200x200
            layoutParams = ViewGroup.LayoutParams(200, 200)
        }

        floatingWidget.findViewById<FrameLayout>(R.id.float_icon_container).addView(floatIcon)

        // Get saved position or use default
        val savedX = sharedPreferences.getInt("FLOATING_ICON_X", 0)
        val savedY = sharedPreferences.getInt("FLOATING_ICON_Y", 0)

        // Set initial layout parameters for the floating widget
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

        // Initialize the close area with the "X" icon
        closeArea = FrameLayout(this).apply {
            setBackgroundResource(R.drawable.rounded_close_area)
            visibility = View.GONE

            // Add the "X" icon inside the close area
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

        // Layout parameters for the close area
        val closeParams = WindowManager.LayoutParams(
            450, // Width of the close area
            150, // Height of the close area
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100 // Offset from the bottom if needed
        }

        windowManager.addView(closeArea, closeParams)

        // Set up initial dimensions for portrait mode
        updateDimensions()

        // Set up the touch listener for moving the floating icon
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

                        // Save position on press down
                        savePosition(initialX, initialY)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // Update the floating icon position
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingWidget, params)

                        // Show the close area with rounded background when in the target area
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
                            stopSelf()
                        }
                        closeArea.visibility = View.GONE
                        return true
                    }
                }
                return false
            }
        })
    }

    // Save position to SharedPreferences
    private fun savePosition(x: Int, y: Int) {
        val editor = sharedPreferences.edit()
        editor.putInt("FLOATING_ICON_X", x)
        editor.putInt("FLOATING_ICON_Y", y)
        editor.apply()
    }

    // Update dimensions based on screen orientation
    private fun updateDimensions() {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels

        // Recalculate target area bounds based on screen dimensions
        bottomAreaThresholdY = screenHeight * 4 / 5
        middleAreaLeftBound = (screenWidth / 2) - 225
        middleAreaRightBound = (screenWidth / 2) + 225
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Update layout dimensions
        updateDimensions()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (this::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        windowManager.removeView(floatingWidget)
        windowManager.removeView(closeArea)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
