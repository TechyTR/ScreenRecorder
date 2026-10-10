package com.nevruz.videor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

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

    companion object {
        private const val REQUEST_MICROPHONE = 1201
    }

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

        if (audioMode == AudioMode.MICROPHONE ||
            audioMode == AudioMode.MICROPHONE_AND_MEDIA
        ) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_MICROPHONE
                )
                return
            }
        }

        requestProjectionConsent()
    }

    private fun requestProjectionConsent() {
        startActivity(Intent(this, ProjectionPermissionActivity::class.java))
        finish()
    }

    private fun openCropSelection() {

        val intent =
            Intent(
                this,
                CropSelectionActivity::class.java
            )

        startActivity(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MICROPHONE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestProjectionConsent()
        } else {
            Toast.makeText(this, "Mikrofon izni verilmedi.", Toast.LENGTH_SHORT).show()
        }
    }

}
