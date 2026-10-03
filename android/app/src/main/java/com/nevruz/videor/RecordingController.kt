package com.nevruz.videor

import android.content.Context
import android.content.Intent

object RecordingController {

    fun start(
        context: Context,
        resultCode: Int,
        data: Intent,
        width: Int,
        height: Int,
        fps: Int
    ) {
        val intent =
            Intent(
                context,
                ScreenRecordService::class.java
            ).apply {

                action =
                    ScreenRecordService.ACTION_START

                putExtra(
                    ScreenRecordService.EXTRA_RESULT_CODE,
                    resultCode
                )

                putExtra(
                    ScreenRecordService.EXTRA_DATA,
                    data
                )

                putExtra(
                    ScreenRecordService.EXTRA_WIDTH,
                    width
                )

                putExtra(
                    ScreenRecordService.EXTRA_HEIGHT,
                    height
                )

                putExtra(
                    ScreenRecordService.EXTRA_FPS,
                    fps
                )
            }

        context.startForegroundService(intent)
    }

    fun stop(context: Context) {

        val intent =
            Intent(
                context,
                ScreenRecordService::class.java
            ).apply {
                action =
                    ScreenRecordService.ACTION_STOP
            }

        context.startService(intent)
    }
}
