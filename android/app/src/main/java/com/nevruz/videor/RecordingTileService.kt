package com.nevruz.videor

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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
                ProjectionPermissionActivity::class.java
            ).apply {

                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {

            val pendingIntent =
                PendingIntent.getActivity(
                    this,
                    100,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or
                            PendingIntent.FLAG_IMMUTABLE
                )

            startActivityAndCollapse(
                pendingIntent
            )

        } else {

            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onStartListening() {
        super.onStartListening()

        updateTile()
    }

    private fun updateTile() {

        qsTile?.apply {

            label =
                "Ekran Kaydı"

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
