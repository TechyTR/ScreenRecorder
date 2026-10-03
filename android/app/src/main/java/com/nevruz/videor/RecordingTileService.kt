package com.nevruz.videor

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class RecordingTileService : TileService() {

    override fun onClick() {
        super.onClick()

        if (
            ScreenRecordService
                .isCurrentlyRecording
        ) {

            RecordingController.stop(this)

            updateTile()

            return
        }

        val intent =
            Intent(
                this,
                ControlPanelActivity::class.java
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

    override fun onStopListening() {
        super.onStopListening()
    }

    private fun updateTile() {

        qsTile?.apply {

            label = "Ekran Kaydı"

            contentDescription =
                if (
                    ScreenRecordService
                        .isCurrentlyRecording
                ) {
                    "Ekran kaydı devam ediyor"
                } else {
                    "Ekran kaydı"
                }

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
