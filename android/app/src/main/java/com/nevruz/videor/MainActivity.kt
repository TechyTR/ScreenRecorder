package com.nevruz.videor

import android.Manifest
import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
        private const val REQUEST_COUNTDOWN = 1002
        private const val REQUEST_MICROPHONE = 1003
    }

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var addTileButton: Button
    private lateinit var settingsButton: Button

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        projectionManager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        statusText =
            findViewById(R.id.statusText)

        recordButton =
            findViewById(R.id.recordButton)

        addTileButton =
            findViewById(R.id.addTileButton)

        settingsButton =
            findViewById(R.id.settingsButton)

        recordButton.setOnClickListener {
            beginRecordingFlow()
        }

        addTileButton.setOnClickListener {
            requestAddQuickSettingsTile()
        }

        settingsButton.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    ControlPanelActivity::class.java
                )
            )
        }

        updateStatus()
    }

    private fun updateStatus() {

        statusText.text =
            if (
                ScreenRecordService
                    .isCurrentlyRecording
            ) {
                "Kayıt devam ediyor"
            } else {
                "Hazır"
            }

        recordButton.text =
            if (
                ScreenRecordService
                    .isCurrentlyRecording
            ) {
                "KAYDI DURDUR"
            } else {
                "KAYDI BAŞLAT"
            }

        recordButton.setOnClickListener {

            if (
                ScreenRecordService
                    .isCurrentlyRecording
            ) {
                RecordingController.stop(this)
                updateStatus()
            } else {
                beginRecordingFlow()
            }
        }
    }

    private fun beginRecordingFlow() {

        val settings =
            RecordingPreferences.load(this)

        if (
            settings.audioMode ==
            AudioMode.MICROPHONE ||
            settings.audioMode ==
            AudioMode.MICROPHONE_AND_MEDIA
        ) {

            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.RECORD_AUDIO
                    ),
                    REQUEST_MICROPHONE
                )

                return
            }
        }

        startCountdown()
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

    private fun requestAddQuickSettingsTile() {

        if (Build.VERSION.SDK_INT < 33) {

            Toast.makeText(
                this,
                "Bu özellik Android 13 ve üzeri için kullanılabilir.",
                Toast.LENGTH_LONG
            ).show()

            return
        }

        val statusBarManager =
            getSystemService(
                StatusBarManager::class.java
            )

        val componentName =
            ComponentName(
                this,
                RecordingTileService::class.java
            )

        val icon =
            Icon.createWithResource(
                this,
                R.drawable.ic_screen_record
            )

        statusBarManager.requestAddTileService(
            componentName,
            "Ekran Kaydı",
            icon,
            mainExecutor
        ) { result ->

            when (result) {

                StatusBarManager
                    .TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {

                    Toast.makeText(
                        this,
                        "Ekran Kaydı kontrol paneline eklendi.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                StatusBarManager
                    .TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {

                    Toast.makeText(
                        this,
                        "Ekran Kaydı zaten kontrol panelinde.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {

                    Toast.makeText(
                        this,
                        "Kontrol paneline ekleme tamamlanmadı.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode ==
            REQUEST_MICROPHONE
        ) {

            if (
                grantResults.isNotEmpty() &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {

                startCountdown()

            } else {

                statusText.text =
                    "Mikrofon izni verilmedi"
            }
        }
    }

    @Deprecated("Deprecated in Android API")
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

            if (
                resultCode ==
                RESULT_OK
            ) {
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

        recordButton.setOnClickListener {
            RecordingController.stop(this)
            updateStatus()
        }
    }
}
