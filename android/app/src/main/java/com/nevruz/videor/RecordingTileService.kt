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

        updateTile()
    }

    private fun updateTile() {

        qsTile?.apply {

            label = "Ekran Kaydı"

            state =
                if (
                    ScreenRecordService
                        .isCurrentlyRecording
                ) {
                    Tile.STATE_ACTIVE
                } else {
                    Tile.STATE_INACTIVE
                }

            updateTile()
        }
    }
}
