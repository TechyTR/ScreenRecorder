package com.nevruz.videor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast

class ControlPanelActivity : Activity() {

    private lateinit var audioGroup: RadioGroup
    private lateinit var screenGroup: RadioGroup

    private lateinit var audioOff: RadioButton
    private lateinit var audioMedia: RadioButton
    private lateinit var audioMicrophoneMedia: RadioButton
    private lateinit var audioMicrophone: RadioButton

    private lateinit var screenFull: RadioButton
    private lateinit var screenCropped: RadioButton

    private lateinit var startButton: Button

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_control_panel
        )

        bindViews()
        loadSettings()
        setupListeners()
    }

    private fun bindViews() {

        audioGroup =
            findViewById(R.id.audioGroup)

        screenGroup =
            findViewById(R.id.screenGroup)

        audioOff =
            findViewById(R.id.audioOff)

        audioMedia =
            findViewById(R.id.audioMedia)

        audioMicrophoneMedia =
            findViewById(
                R.id.audioMicrophoneMedia
            )

        audioMicrophone =
            findViewById(
                R.id.audioMicrophone
            )

        screenFull =
            findViewById(
                R.id.screenFull
            )

        screenCropped =
            findViewById(
                R.id.screenCropped
            )

        startButton =
            findViewById(
                R.id.startButton
            )
    }

    private fun loadSettings() {

        val settings =
            RecordingPreferences.load(this)

        when (settings.audioMode) {

            AudioMode.OFF -> {
                audioOff.isChecked = true
            }

            AudioMode.MEDIA -> {
                audioMedia.isChecked = true
            }

            AudioMode.MICROPHONE -> {
                audioMicrophone.isChecked = true
            }

            AudioMode.MICROPHONE_AND_MEDIA -> {
                audioMicrophoneMedia.isChecked =
                    true
            }
        }

        when (settings.screenMode) {

            ScreenMode.FULL_SCREEN -> {
                screenFull.isChecked = true
            }

            ScreenMode.CROPPED -> {
                screenCropped.isChecked = true
            }
        }
    }

    private fun setupListeners() {

        startButton.setOnClickListener {
            startRecording()
        }

        screenGroup.setOnCheckedChangeListener {
                _,
                checkedId ->

            if (
                checkedId ==
                R.id.screenCropped
            ) {
                openCropSelection()
            }
        }
    }

    private fun startRecording() {

        val current =
            RecordingPreferences.load(this)

        val audioMode =
            when {

                audioOff.isChecked ->
                    AudioMode.OFF

                audioMedia.isChecked ->
                    AudioMode.MEDIA

                audioMicrophoneMedia.isChecked ->
                    AudioMode.MICROPHONE_AND_MEDIA

                audioMicrophone.isChecked ->
                    AudioMode.MICROPHONE

                else ->
                    AudioMode.OFF
            }

        val screenMode =
            when {

                screenFull.isChecked ->
                    ScreenMode.FULL_SCREEN

                screenCropped.isChecked ->
                    ScreenMode.CROPPED

                else ->
                    ScreenMode.FULL_SCREEN
            }

        RecordingPreferences.save(
            this,
            current.copy(
                audioMode = audioMode,
                screenMode = screenMode
            )
        )

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        val projectionIntent =
            manager.createScreenCaptureIntent()

        startActivityForResult(
            projectionIntent,
            REQUEST_MEDIA_PROJECTION
        )
    }

    private fun openCropSelection() {

        val intent =
            Intent(
                this,
                CropSelectionActivity::class.java
            )

        startActivity(intent)
    }

    @Deprecated(
        "Deprecated in Android API"
    )
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
            REQUEST_MEDIA_PROJECTION &&
            resultCode == RESULT_OK &&
            data != null
        ) {

            val intent =
                Intent(
                    this,
                    CountdownActivity::class.java
                )

            intent.putExtra(
                CountdownActivity.EXTRA_RESULT_CODE,
                resultCode
            )

            intent.putExtra(
                CountdownActivity.EXTRA_DATA,
                data
            )

            startActivity(intent)

            finish()

        } else {

            Toast.makeText(
                this,
                "Ekran kaydı izni verilmedi.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {

        const val REQUEST_MEDIA_PROJECTION =
            1001
    }
}
