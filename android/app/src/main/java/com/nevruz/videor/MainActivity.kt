package com.nevruz.videor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
    }

    private lateinit var projectionManager: MediaProjectionManager

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button

    private lateinit var cardFhd60: MaterialCardView
    private lateinit var cardQhd60: MaterialCardView
    private lateinit var cardQhd120: MaterialCardView

    private lateinit var fhdSelectedText: TextView
    private lateinit var qhd60SelectedText: TextView
    private lateinit var qhd120SelectedText: TextView

    private var selectedWidth = 2340
    private var selectedHeight = 1080
    private var selectedFps = 60

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        statusText = findViewById(R.id.statusText)
        recordButton = findViewById(R.id.recordButton)

        cardFhd60 = findViewById(R.id.cardFhd60)
        cardQhd60 = findViewById(R.id.cardQhd60)
        cardQhd120 = findViewById(R.id.cardQhd120)

        fhdSelectedText = findViewById(R.id.fhdSelectedText)
        qhd60SelectedText = findViewById(R.id.qhd60SelectedText)
        qhd120SelectedText = findViewById(R.id.qhd120SelectedText)

        cardFhd60.setOnClickListener {
            selectFHD60()
        }

        cardQhd60.setOnClickListener {
            selectQHD60()
        }

        cardQhd120.setOnClickListener {
            selectQHD120()
        }

        recordButton.setOnClickListener {
            requestRecordingPermission()
        }

        updateSelectionUI()
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

        if (requestCode != REQUEST_MEDIA_PROJECTION) {
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

        setProfileCardsEnabled(false)

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

        statusText.text = "Hazır"

        recordButton.text =
            "KAYIT BAŞLAT"

        setProfileCardsEnabled(true)

        recordButton.setOnClickListener {
            requestRecordingPermission()
        }
    }

    private fun selectFHD60() {

        selectedWidth = 2340
        selectedHeight = 1080
        selectedFps = 60

        updateSelectionUI()
    }

    private fun selectQHD60() {

        selectedWidth = 3120
        selectedHeight = 1440
        selectedFps = 60

        updateSelectionUI()
    }

    private fun selectQHD120() {

        selectedWidth = 3120
        selectedHeight = 1440
        selectedFps = 120

        updateSelectionUI()
    }

    private fun updateSelectionUI() {

        fhdSelectedText.visibility =
            if (
                selectedWidth == 2340 &&
                selectedHeight == 1080 &&
                selectedFps == 60
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        qhd60SelectedText.visibility =
            if (
                selectedWidth == 3120 &&
                selectedHeight == 1440 &&
                selectedFps == 60
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }

        qhd120SelectedText.visibility =
            if (
                selectedWidth == 3120 &&
                selectedHeight == 1440 &&
                selectedFps == 120
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun setProfileCardsEnabled(
        enabled: Boolean
    ) {

        cardFhd60.isEnabled = enabled
        cardQhd60.isEnabled = enabled
        cardQhd120.isEnabled = enabled

        cardFhd60.alpha =
            if (enabled) 1f else 0.5f

        cardQhd60.alpha =
            if (enabled) 1f else 0.5f

        cardQhd120.alpha =
            if (enabled) 1f else 0.5f
    }
}
