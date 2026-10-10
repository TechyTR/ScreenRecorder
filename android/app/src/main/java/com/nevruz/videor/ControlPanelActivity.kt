package com.nevruz.videor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class ControlPanelActivity : Activity() {

    private lateinit var audioGroup: RadioGroup
    private lateinit var screenGroup: RadioGroup
    private lateinit var resolutionSpinner: Spinner
    private lateinit var audioOff: RadioButton
    private lateinit var audioMedia: RadioButton
    private lateinit var audioMicrophone: RadioButton
    private lateinit var audioMicrophoneMedia: RadioButton
    private lateinit var screenFull: RadioButton
    private lateinit var screenCropped: RadioButton
    private lateinit var startButton: Button

    private lateinit var resolutionOptions: List<ResolutionOption>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setGravity(Gravity.CENTER)
        window.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        setContentView(R.layout.activity_control_panel)
        bindViews()
        setupResolutionOptions()
        loadSettings()

        startButton.setOnClickListener { requestMicrophoneThenProjection() }
        screenGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.screenCropped) openCropSelection()
        }
    }

    private fun bindViews() {
        audioGroup = findViewById(R.id.audioGroup)
        screenGroup = findViewById(R.id.screenGroup)
        resolutionSpinner = findViewById(R.id.resolutionSpinner)
        audioOff = findViewById(R.id.audioOff)
        audioMedia = findViewById(R.id.audioMedia)
        audioMicrophone = findViewById(R.id.audioMicrophone)
        audioMicrophoneMedia = findViewById(R.id.audioMicrophoneMedia)
        screenFull = findViewById(R.id.screenFull)
        screenCropped = findViewById(R.id.screenCropped)
        startButton = findViewById(R.id.startButton)
    }

    private fun setupResolutionOptions() {
        val display = resources.displayMetrics
        val shortSide = minOf(display.widthPixels, display.heightPixels)
        val longSide = maxOf(display.widthPixels, display.heightPixels)
        val native60 = ResolutionOption("Cihaz çözünürlüğü", display.widthPixels, display.heightPixels, 60)
        val native30 = ResolutionOption("Cihaz çözünürlüğü", display.widthPixels, display.heightPixels, 30)
        val scaledLong = (longSide * 720f / shortSide).toInt() and 1.inv()
        val hd = if (display.widthPixels <= display.heightPixels) {
            ResolutionOption("720p", 720, scaledLong, 30)
        } else {
            ResolutionOption("720p", scaledLong, 720, 30)
        }
        resolutionOptions = listOf(native60, native30, hd)
        resolutionSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resolutionOptions
        )
    }

    private fun loadSettings() {
        val settings = RecordingPreferences.load(this)
        when (settings.audioMode) {
            AudioMode.OFF -> audioOff.isChecked = true
            AudioMode.MEDIA -> audioMedia.isChecked = true
            AudioMode.MICROPHONE -> audioMicrophone.isChecked = true
            AudioMode.MICROPHONE_AND_MEDIA -> audioMicrophoneMedia.isChecked = true
        }
        if (settings.screenMode == ScreenMode.CROPPED) screenCropped.isChecked = true
        else screenFull.isChecked = true

        val savedIndex = resolutionOptions.indexOfFirst {
            it.width == settings.width && it.height == settings.height && it.fps == settings.fps
        }
        resolutionSpinner.setSelection(if (savedIndex >= 0) savedIndex else 0)
    }

    private fun requestMicrophoneThenProjection() {
        val audioMode = selectedAudioMode()
        if ((audioMode == AudioMode.MICROPHONE || audioMode == AudioMode.MICROPHONE_AND_MEDIA) &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE)
            return
        }
        requestProjection()
    }

    private fun requestProjection() {
        val quality = resolutionSpinner.selectedItem as ResolutionOption
        RecordingPreferences.save(
            this,
            RecordingSettings(
                audioMode = selectedAudioMode(),
                screenMode = if (screenCropped.isChecked) ScreenMode.CROPPED else ScreenMode.FULL_SCREEN,
                width = quality.width,
                height = quality.height,
                fps = quality.fps
            )
        )
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    private fun selectedAudioMode() = when {
        audioMedia.isChecked -> AudioMode.MEDIA
        audioMicrophone.isChecked -> AudioMode.MICROPHONE
        audioMicrophoneMedia.isChecked -> AudioMode.MICROPHONE_AND_MEDIA
        else -> AudioMode.OFF
    }

    private fun openCropSelection() {
        startActivity(Intent(this, CropSelectionActivity::class.java))
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MEDIA_PROJECTION) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Ekran kaydı izni verilmedi.", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(this, CountdownActivity::class.java).apply {
            putExtra(CountdownActivity.EXTRA_RESULT_CODE, resultCode)
            putExtra(CountdownActivity.EXTRA_DATA, data)
        })
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_MICROPHONE && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestProjection()
        } else if (requestCode == REQUEST_MICROPHONE) {
            Toast.makeText(this, "Mikrofon izni verilmedi.", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
        private const val REQUEST_MICROPHONE = 1002
    }
}
