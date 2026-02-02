package com.djlactose.energydrink

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class FloatingWidgetTileService : TileService() {

    override fun onClick() {
        val tile = qsTile
        if (tile.state == Tile.STATE_INACTIVE) {
            // Activate the tile and start the floating icon service
            tile.state = Tile.STATE_ACTIVE
            val intent = Intent(this, FloatingWidgetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } else {
            // Deactivate the tile and stop the floating icon service
            tile.state = Tile.STATE_INACTIVE
            stopService(Intent(this, FloatingWidgetService::class.java))
        }
        tile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        // Update the tile state based on whether the FloatingWidgetService is running
        if (FloatingWidgetService.isServiceRunning) {
            tile.state = Tile.STATE_ACTIVE
        } else {
            tile.state = Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        // Handle any cleanup if needed
    }
}
