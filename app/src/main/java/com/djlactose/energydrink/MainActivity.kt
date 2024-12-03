package com.djlactose.energydrink

import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Dynamically register PowerButtonReceiver
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(PowerButtonReceiver(), filter)

        // Request overlay permission if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 100)
        }

        // Start the floating widget service and close the main activity
        findViewById<Button>(R.id.startServiceButton).setOnClickListener {
            val intent = Intent(this, FloatingWidgetService::class.java)
            startService(intent)
            finish()
        }

        // Reference the toggle and save its state
        val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val powerButtonToggle: Switch = findViewById(R.id.powerButtonToggle)
        val isShutdownOnPowerPress = preferences.getBoolean("shutdown_on_power", false)
        powerButtonToggle.isChecked = isShutdownOnPowerPress

        powerButtonToggle.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("shutdown_on_power", isChecked).apply()
        }
    }
}
