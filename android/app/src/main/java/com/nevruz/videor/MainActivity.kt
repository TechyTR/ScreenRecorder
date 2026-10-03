package com.nevruz.videor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

class MainActivity : Activity() {

    companion object {

        const val ACTION_START_FROM_PANEL =
            "com.nevruz.videor.action.START_FROM_PANEL"

        private const val REQUEST_MEDIA_PROJECTION = 1001
        private const val REQUEST_COUNTDOWN = 1002
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

    private var startFromPanel = false

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_main
        )

        projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        statusText =
            findViewById(R.id.statusText)

        recordButton =
            findViewById(R.id.recordButton)

        cardFhd60 =
            findViewById(R.id.cardFhd60)

        cardQhd60 =
            findViewById(R.id.cardQhd60)

        cardQhd120 =
            findViewById(R.id.cardQhd120)

        fhdSelectedText =
            findViewById(R.id.fhdSelectedText)

        qhd60SelectedText =
            findViewById(R.id.qhd60SelectedText)

        qhd120SelectedText =
            findViewById(R.id.qhd120SelectedText)

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

        if (
            intent?.action ==
            ACTION_START_FROM_PANEL
        ) {
            startFromPanel = true

            val settings =
                RecordingPreferences.load(this)

            selectedWidth =
                settings.width

            selectedHeight =
                settings.height

            selectedFps =
                settings.fps

            updateSelectionUI()

            startCountdown()
        } else {
            updateSelectionUI()
        }
    }

    private fun startCountdown() {

        startActivityForResult(
            Intent(
                this,
                CountdownActivity::class.java
            ),
            REQUEST_COUNTDOWN
        )
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
            requestCode ==
            REQUEST_COUNTDOWN
        ) {

            if (resultCode == RESULT_OK) {
                requestRecordingPermission()
            }

            return
        }

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

        val settings =
            RecordingPreferences.load(this)

        RecordingController.start(
            context = this,
            resultCode = resultCode,
            data = data,
            width = settings.width,
            height = settings.height,
            fps = settings.fps
        )

        statusText.text =
            "${settings.width}×${settings.height} • ${settings.fps} FPS"

        recordButton.text =
            "KAYDI DURDUR"

        setProfileCardsEnabled(false)

        recordButton.setOnClickListener {
            stopRecording()
        }

        startFromPanel = false
    }

    private fun stopRecording() {

        RecordingController.stop(this)

        statusText.text =
            "Hazır"

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

        saveCurrentProfile()
        updateSelectionUI()
    }

    private fun selectQHD60() {

        selectedWidth = 3120
        selectedHeight = 1440
        selectedFps = 60

        saveCurrentProfile()
        updateSelectionUI()
    }

    private fun selectQHD120() {

        selectedWidth = 3120
        selectedHeight = 1440
        selectedFps = 120

        saveCurrentProfile()
        updateSelectionUI()
    }

    private fun saveCurrentProfile() {

        val old =
            RecordingPreferences.load(this)

        RecordingPreferences.save(
            this,
            old.copy(
                width = selectedWidth,
                height = selectedHeight,
                fps = selectedFps
            )
        )
    }

    private fun updateSelectionUI() {

        fhdSelectedText.visibility =
            if (
                selectedWidth == 2340 &&
                selectedHeight == 1080 &&
                selectedFps == 60
            ) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

        qhd60SelectedText.visibility =
            if (
                selectedWidth == 3120 &&
                selectedHeight == 1440 &&
                selectedFps == 60
            ) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
            }

        qhd120SelectedText.visibility =
            if (
                selectedWidth == 3120 &&
                selectedHeight == 1440 &&
                selectedFps == 120
            ) {
                android.view.View.VISIBLE
            } else {
                android.view.View.GONE
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
