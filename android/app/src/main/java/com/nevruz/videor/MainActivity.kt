package com.nevruz.videor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001
    }

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusText: TextView
    private lateinit var recordButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        recordButton = findViewById(R.id.recordButton)

        projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        recordButton.setOnClickListener {
            startRecordingRequest()
        }

        updateUI()
    }

    private fun startRecordingRequest() {
        val intent = projectionManager.createScreenCaptureIntent()
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
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_MEDIA_PROJECTION) {
            return
        }

        if (resultCode != RESULT_OK || data == null) {
            statusText.text = "Kayıt izni verilmedi"
            return
        }

        val serviceIntent = Intent(
            this,
            ScreenRecordService::class.java
        ).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(
                ScreenRecordService.EXTRA_RESULT_CODE,
                resultCode
            )
            putExtra(
                ScreenRecordService.EXTRA_DATA,
                data
            )
        }

        startForegroundService(serviceIntent)

        statusText.text = "Kayıt yapılıyor"
        recordButton.text = "KAYDI DURDUR"

        recordButton.setOnClickListener {
            stopRecording()
        }
    }

    private fun stopRecording() {
        val intent = Intent(
            this,
            ScreenRecordService::class.java
        ).apply {
            action = ScreenRecordService.ACTION_STOP
        }

        startService(intent)

        statusText.text = "Hazır"
        recordButton.text = "KAYIT BAŞLAT"

        recordButton.setOnClickListener {
            startRecordingRequest()
        }
    }

    private fun updateUI() {
        statusText.text = "Hazır"
        recordButton.text = "KAYIT BAŞLAT"
    }
}
