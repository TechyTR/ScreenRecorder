package com.nevruz.videor

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

class QuickSettingsStateReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        if (
            intent.action !=
            ScreenRecordService.ACTION_STATE_CHANGED
        ) {
            return
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.N
        ) {

            TileService.requestListeningState(
                context,
                ComponentName(
                    context,
                    RecordingTileService::class.java
                )
            )
        }
    }
}
