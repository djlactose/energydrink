package com.djlactose.energydrink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.djlactose.energydrink.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Timeout options
    private val timeoutOptions = arrayOf("Off", "5 minutes", "15 minutes", "30 minutes", "1 hour", "2 hours")
    private val timeoutValues = longArrayOf(0, 5*60*1000, 15*60*1000, 30*60*1000, 60*60*1000, 120*60*1000)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateStartButtonState()
    }

    private val iconPickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            try {
                // Take persistable permission so we can access it later
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
                preferences.edit().putString("custom_icon_uri", it.toString()).apply()
                binding.customIconPreview.setImageURI(it)
                binding.clearIconButton.visibility = View.VISIBLE
            } catch (e: SecurityException) {
                // Failed to take permission, save URI anyway
                val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
                preferences.edit().putString("custom_icon_uri", it.toString()).apply()
                binding.customIconPreview.setImageURI(it)
                binding.clearIconButton.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)

        // Request overlay permission if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }

        // Update start button state based on permission
        updateStartButtonState()

        // Start the floating widget service and close the main activity
        binding.startServiceButton.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                overlayPermissionLauncher.launch(intent)
                return@setOnClickListener
            }

            val intent = Intent(this, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            finish()
        }

        // Power button toggle
        val isShutdownOnPowerPress = preferences.getBoolean("shutdown_on_power", false)
        binding.powerButtonToggle.isChecked = isShutdownOnPowerPress
        binding.powerButtonToggle.setOnCheckedChangeListener { _, isChecked ->
            preferences.edit().putBoolean("shutdown_on_power", isChecked).apply()
        }

        // Opacity slider
        val savedOpacity = preferences.getInt("widget_opacity", 100)
        binding.opacitySlider.progress = savedOpacity
        binding.opacityValue.text = "$savedOpacity%"
        binding.opacitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.opacityValue.text = "$progress%"
                if (fromUser) {
                    preferences.edit().putInt("widget_opacity", progress).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Timeout spinner
        binding.timeoutSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, timeoutOptions)
        binding.timeoutSpinner.setSelection(preferences.getInt("timeout_index", 0))
        binding.timeoutSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                preferences.edit()
                    .putInt("timeout_index", position)
                    .putLong("timeout_ms", timeoutValues[position])
                    .apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Custom icon picker
        val customIconUri = preferences.getString("custom_icon_uri", null)
        if (customIconUri != null) {
            try {
                binding.customIconPreview.setImageURI(Uri.parse(customIconUri))
                binding.clearIconButton.visibility = View.VISIBLE
            } catch (e: Exception) {
                binding.customIconPreview.setImageResource(R.drawable.energy_drink_floating)
                binding.clearIconButton.visibility = View.GONE
            }
        }

        binding.selectIconButton.setOnClickListener {
            iconPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.clearIconButton.setOnClickListener {
            preferences.edit().remove("custom_icon_uri").apply()
            binding.customIconPreview.setImageResource(R.drawable.energy_drink_floating)
            binding.clearIconButton.visibility = View.GONE
        }

        // Check battery optimization on first launch
        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val preferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val hasAskedBefore = preferences.getBoolean("asked_battery_optimization", false)

            if (!powerManager.isIgnoringBatteryOptimizations(packageName) && !hasAskedBefore) {
                AlertDialog.Builder(this)
                    .setTitle("Battery Optimization")
                    .setMessage("For reliable screen wake functionality, please disable battery optimization for this app.")
                    .setPositiveButton("Settings") { _, _ ->
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    }
                    .setNegativeButton("Later") { _, _ ->
                        preferences.edit().putBoolean("asked_battery_optimization", true).apply()
                    }
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStartButtonState()
    }

    private fun updateStartButtonState() {
        val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        binding.startServiceButton.isEnabled = hasOverlayPermission
        binding.startServiceButton.alpha = if (hasOverlayPermission) 1.0f else 0.5f
    }
}
