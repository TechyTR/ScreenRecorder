package com.nevruz.videor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    companion object {

        private const val REQUEST_MEDIA_PROJECTION =
            1001
    }

    private lateinit var projectionManager:
            MediaProjectionManager

    private lateinit var statusText:
            TextView

    private lateinit var recordButton:
            Button

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        statusText =
            findViewById(R.id.statusText)

        recordButton =
            findViewById(R.id.recordButton)

        projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        recordButton.setOnClickListener {

            requestRecordingPermission()
        }
    }

    private fun requestRecordingPermission() {

        val intent =
            projectionManager
                .createScreenCaptureIntent()

        startActivityForResult(
            intent,
            REQUEST_MEDIA_PROJECTION
        )
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            requestCode !=
            REQUEST_MEDIA_PROJECTION
        ) {
            return
        }

        if (
            resultCode != RESULT_OK ||
            data == null
        ) {

            statusText.text =
                "Kayıt izni verilmedi"

            return
        }

        val serviceIntent =
            Intent(
                this,
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

                /*
                 * S25+ QHD+
                 *
                 * Dikey:
                 * 1440 x 3120
                 */

                putExtra(
                    ScreenRecordService.EXTRA_WIDTH,
                    1440
                )

                putExtra(
                    ScreenRecordService.EXTRA_HEIGHT,
                    3120
                )

                /*
                 * Test:
                 *
                 * 120 FPS
                 */

                putExtra(
                    ScreenRecordService.EXTRA_FPS,
                    120
                )
            }

        startForegroundService(
            serviceIntent
        )

        statusText.text =
            "QHD+ / 120 FPS kayıt yapılıyor"

        recordButton.text =
            "KAYDI DURDUR"

        recordButton.setOnClickListener {

            stopRecording()
        }
    }

    private fun stopRecording() {

        val intent =
            Intent(
                this,
                ScreenRecordService::class.java
            ).apply {

                action =
                    ScreenRecordService.ACTION_STOP
            }

        startService(intent)

        statusText.text =
            "Hazır"

        recordButton.text =
            "KAYIT BAŞLAT"

        recordButton.setOnClickListener {

            requestRecordingPermission()
        }
    }
}
