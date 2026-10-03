package com.nevruz.videor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
    }

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusText: TextView
    private lateinit var recordButton: Button

    private var selectedWidth = 2340
    private var selectedHeight = 1080
    private var selectedFps = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        recordButton = findViewById(R.id.recordButton)

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
            projectionManager.createScreenCaptureIntent()

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
            requestCode != REQUEST_MEDIA_PROJECTION
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

                putExtra(
                    ScreenRecordService.EXTRA_WIDTH,
                    selectedWidth
                )

                putExtra(
                    ScreenRecordService.EXTRA_HEIGHT,
                    selectedHeight
                )

                putExtra(
                    ScreenRecordService.EXTRA_FPS,
                    selectedFps
                )
            }

        startForegroundService(
            serviceIntent
        )

        statusText.text =
            "${selectedWidth}×${selectedHeight} • ${selectedFps} FPS"

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

    /*
     * HD 30 FPS
     */
    private fun selectHD() {

        selectedWidth = 1280
        selectedHeight = 720
        selectedFps = 30
    }

    /*
     * FHD+ 60 FPS
     */
    private fun selectFHD60() {

        selectedWidth = 2340
        selectedHeight = 1080
        selectedFps = 60
    }

    /*
     * QHD+ 60 FPS
     */
    private fun selectQHD60() {

        selectedWidth = 3120
        selectedHeight = 1440
        selectedFps = 60
    }

    /*
     * QHD+ 120 FPS
     *
     * Deneysel.
     */
    private fun selectQHD120() {

        selectedWidth = 3120
        selectedHeight = 1440
        selectedFps = 120
    }
}
