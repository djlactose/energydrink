package com.djlactose.energydrink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

class PowerButtonReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_FINISH_APP = "com.djlactose.energydrink.FINISH_APP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("PowerButtonReceiver", "Received broadcast: ${intent.action}")

        if (Intent.ACTION_SCREEN_OFF == intent.action) {
            val preferences: SharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val isShutdownOnPowerPress = preferences.getBoolean("shutdown_on_power", false)

            if (isShutdownOnPowerPress) {
                Log.d("PowerButtonReceiver", "Shutting down app and stopping floating service.")

                // Stop the floating widget service
                val serviceIntent = Intent(context, FloatingWidgetService::class.java)
                context.stopService(serviceIntent)

                // Send broadcast to finish activities gracefully
                val finishIntent = Intent(ACTION_FINISH_APP)
                finishIntent.setPackage(context.packageName)
                context.sendBroadcast(finishIntent)
            } else {
                Log.d("PowerButtonReceiver", "Shutdown is disabled. Doing nothing.")
            }
        }
    }
}

