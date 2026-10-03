package com.nevruz.videor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RecordingControlReceiver :
    BroadcastReceiver() {

    override fun onReceive(
        context: Context,
        intent: Intent
    ) {

        when (intent.action) {

            RecordingControlAction.ACTION_PAUSE -> {

                context.startService(
                    Intent(
                        context,
                        ScreenRecordService::class.java
                    ).apply {
                        action =
                            RecordingControlAction
                                .ACTION_PAUSE
                    }
                )
            }

            RecordingControlAction.ACTION_RESUME -> {

                context.startService(
                    Intent(
                        context,
                        ScreenRecordService::class.java
                    ).apply {
                        action =
                            RecordingControlAction
                                .ACTION_RESUME
                    }
                )
            }

            RecordingControlAction.ACTION_STOP -> {

                context.startService(
                    Intent(
                        context,
                        ScreenRecordService::class.java
                    ).apply {
                        action =
                            ScreenRecordService
                                .ACTION_STOP
                    }
                )
            }

            RecordingControlAction.ACTION_DRAW -> {

                context.startService(
                    Intent(
                        context,
                        ScreenRecordService::class.java
                    ).apply {
                        action =
                            RecordingControlAction
                                .ACTION_DRAW
                    }
                )
            }
        }
    }
}
