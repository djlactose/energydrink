package com.djlactose.energydrink

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.app.NotificationCompat
import androidx.core.view.isVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class FloatingWidgetService : Service() {

    companion object {
        var isServiceRunning = false
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "energy_drink_channel"

        // Constants for widget behavior
        private const val ICON_SIZE = 200
        private const val CLOSE_AREA_WIDTH = 450
        private const val CLOSE_AREA_HEIGHT = 150
        private const val CLOSE_AREA_MARGIN_BOTTOM = 100
        private const val CLOSE_AREA_HALF_WIDTH = 225
        private const val FRICTION = 0.985f  // Higher = longer glide
        private const val VELOCITY_THRESHOLD = 50f  // pixels per second
        private const val VELOCITY_MULTIPLIER = 0.018f  // ~1/60 to convert px/s to px/frame
        private const val FRAME_DELAY_MS = 16L
        private const val SNAP_DURATION_MS = 200L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingWidget: View
    private lateinit var floatIcon: ImageView
    private lateinit var closeArea: FrameLayout
    private lateinit var closeIcon: ImageView
    private lateinit var preferences: SharedPreferences
    private lateinit var appPrefs: SharedPreferences
    private lateinit var params: WindowManager.LayoutParams

    // Screen area thresholds
    private var bottomAreaThresholdY: Int = 0
    private var middleAreaLeftBound: Int = 0
    private var middleAreaRightBound: Int = 0

    // VelocityTracker for smooth fling detection
    private var velocityTracker: VelocityTracker? = null

    // Power button receiver to stop service when screen turns off
    private var powerButtonReceiver: BroadcastReceiver? = null

    // Coroutine scope for animations
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var flingJob: Job? = null
    private var timeoutJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true

        // Create notification channel and start as foreground service
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        preferences = getSharedPreferences("FloatingWidgetPrefs", Context.MODE_PRIVATE)

        // Load saved position
        val savedX = preferences.getInt("floatIconX", 0)
        val savedY = preferences.getInt("floatIconY", 0)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate your custom layout (which includes a FrameLayout for the icon container)
        floatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_close, null)

        floatIcon = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ICON_SIZE, ICON_SIZE)
        }
        floatingWidget.findViewById<FrameLayout>(R.id.float_icon_container).addView(floatIcon)

        // Load settings from preferences
        appPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Apply custom icon if set
        val customIconUri = appPrefs.getString("custom_icon_uri", null)
        if (customIconUri != null) {
            try {
                floatIcon.setImageURI(Uri.parse(customIconUri))
            } catch (e: Exception) {
                floatIcon.setImageResource(R.drawable.energy_drink_floating)
            }
        } else {
            floatIcon.setImageResource(R.drawable.energy_drink_floating)
        }

        // Apply opacity
        val opacity = appPrefs.getInt("widget_opacity", 100)
        floatIcon.alpha = opacity / 100f

        // Window LayoutParams for the floating widget
        // FLAG_KEEP_SCREEN_ON keeps the screen on while the widget is visible
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = savedX
            y = savedY
        }

        windowManager.addView(floatingWidget, params)

        // Clamp saved position to screen bounds after layout (dimensions are 0 before layout)
        floatingWidget.post {
            clampXAndYToScreen()
            windowManager.updateViewLayout(floatingWidget, params)
        }

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
            CLOSE_AREA_WIDTH,
            CLOSE_AREA_HEIGHT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = CLOSE_AREA_MARGIN_BOTTOM
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
                        flingJob?.cancel()

                        // Initialize velocity tracker
                        velocityTracker?.recycle()
                        velocityTracker = VelocityTracker.obtain()
                        velocityTracker?.addMovement(event)

                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY

                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.addMovement(event)

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

                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        // Compute velocity
                        velocityTracker?.addMovement(event)
                        velocityTracker?.computeCurrentVelocity(1000) // pixels per second
                        val vX = velocityTracker?.xVelocity ?: 0f
                        val vY = velocityTracker?.yVelocity ?: 0f
                        velocityTracker?.recycle()
                        velocityTracker = null

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

                        // If velocity is small, snap to edge immediately.
                        // Otherwise, start fling (which will snap after completing).
                        if (abs(vX) < VELOCITY_THRESHOLD && abs(vY) < VELOCITY_THRESHOLD) {
                            snapToNearestEdge()
                        } else {
                            startFlingAnimation(vX, vY)
                        }

                        return true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        velocityTracker?.recycle()
                        velocityTracker = null
                        return true
                    }
                }
                return false
            }
        })

        // Register/unregister power button receiver based on current setting
        updatePowerButtonReceiver(appPrefs.getBoolean("shutdown_on_power", false))

        // Listen for preference changes so toggling the setting takes effect immediately
        appPrefs.registerOnSharedPreferenceChangeListener(prefListener)

        // Start auto-timeout if enabled
        startTimeoutIfEnabled(appPrefs)
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == "shutdown_on_power") {
            updatePowerButtonReceiver(prefs.getBoolean(key, false))
        }
    }

    private fun updatePowerButtonReceiver(enabled: Boolean) {
        // Unregister existing receiver if any
        powerButtonReceiver?.let {
            try { unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
        }
        powerButtonReceiver = null

        if (enabled) {
            powerButtonReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (Intent.ACTION_SCREEN_OFF == intent.action) {
                        stopSelf()
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(powerButtonReceiver, filter, RECEIVER_EXPORTED)
            } else {
                registerReceiver(powerButtonReceiver, filter)
            }
        }
    }

    /**
     * Start the auto-timeout countdown if enabled.
     */
    private fun startTimeoutIfEnabled(prefs: SharedPreferences) {
        val timeoutMs = prefs.getLong("timeout_ms", 0)
        if (timeoutMs > 0) {
            timeoutJob = serviceScope.launch {
                delay(timeoutMs)
                stopSelf()
            }
        }
    }

    /**
     * Start a fling animation using coroutines.
     * The icon continues moving after the user lifts their finger,
     * gradually slowing down due to friction until it stops.
     */
    private fun startFlingAnimation(vX: Float, vY: Float) {
        flingJob?.cancel()
        flingJob = serviceScope.launch {
            var localVx = vX * VELOCITY_MULTIPLIER
            var localVy = vY * VELOCITY_MULTIPLIER

            while (isActive && (abs(localVx) > 0.5f || abs(localVy) > 0.5f)) {
                // Apply friction
                localVx *= FRICTION
                localVy *= FRICTION

                // Update position
                params.x += localVx.toInt()
                params.y += localVy.toInt()

                // Clamp to screen bounds
                clampXAndYToScreen()

                // Update the floating widget
                windowManager.updateViewLayout(floatingWidget, params)

                delay(FRAME_DELAY_MS)
            }

            // Snap to edge after fling completes
            snapToNearestEdge()
        }
    }

    /**
     * Smoothly animate the widget to the nearest horizontal edge.
     */
    private fun snapToNearestEdge() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val widgetWidth = floatingWidget.width

        // Determine target X: left edge (0) or right edge
        val targetX = if (params.x < screenWidth / 2) 0 else screenWidth - widgetWidth

        flingJob?.cancel()
        flingJob = serviceScope.launch {
            val startX = params.x
            val startTime = System.currentTimeMillis()

            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = (elapsed.toFloat() / SNAP_DURATION_MS).coerceAtMost(1f)

                // Ease-out interpolation for smooth deceleration
                val easeOut = 1f - (1f - progress) * (1f - progress)
                params.x = (startX + (targetX - startX) * easeOut).toInt()

                windowManager.updateViewLayout(floatingWidget, params)

                if (progress >= 1f) break
                delay(FRAME_DELAY_MS)
            }

            savePosition(params.x, params.y)
        }
    }

    /** Save the floating icon's position to SharedPreferences. */
    private fun savePosition(x: Int, y: Int) {
        preferences.edit().apply {
            putInt("floatIconX", x)
            putInt("floatIconY", y)
            apply()
        }
    }

    /** Clamp X and Y so the icon stays on screen. */
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
        middleAreaLeftBound = (displayMetrics.widthPixels / 2) - CLOSE_AREA_HALF_WIDTH
        middleAreaRightBound = (displayMetrics.widthPixels / 2) + CLOSE_AREA_HALF_WIDTH
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateDimensions()
        // Reposition widget to stay on-screen after screen size change
        floatingWidget.post {
            clampXAndYToScreen()
            windowManager.updateViewLayout(floatingWidget, params)
            savePosition(params.x, params.y)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Energy Drink Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the screen on while the floating widget is active"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Energy Drink Active")
            .setContentText("Screen will stay on")
            .setSmallIcon(R.drawable.ic_quick_tile)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false

        // Request Quick Settings tile to refresh its state
        TileService.requestListeningState(this, ComponentName(this, FloatingWidgetTileService::class.java))

        // Unregister preference listener and power button receiver
        appPrefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        powerButtonReceiver?.let {
            try { unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
        }
        powerButtonReceiver = null

        // Cancel all coroutines
        flingJob?.cancel()
        timeoutJob?.cancel()
        serviceScope.cancel()

        // Clean up velocity tracker
        velocityTracker?.recycle()
        velocityTracker = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        windowManager.removeView(floatingWidget)
        windowManager.removeView(closeArea)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
