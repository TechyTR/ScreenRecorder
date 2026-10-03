package com.nevruz.videor

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class RecordingTileService : TileService() {

    override fun onClick() {
        super.onClick()

        val intent =
            Intent(
                this,
                MainActivity::class.java
            ).apply {

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }

        startActivityAndCollapse(intent)
    }

    override fun onStartListening() {
        super.onStartListening()

        qsTile?.apply {

            label = "Ekran Kaydı"
            state = Tile.STATE_INACTIVE

            updateTile()
        }
    }
}
