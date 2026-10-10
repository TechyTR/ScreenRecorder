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
        private const val REQUEST_MICROPHONE = 1003
    }

    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var addTileButton: Button
    private lateinit var settingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        statusText = findViewById(R.id.statusText)
        recordButton = findViewById(R.id.recordButton)
        addTileButton = findViewById(R.id.addTileButton)
        settingsButton = findViewById(R.id.settingsButton)

        recordButton.setOnClickListener {
            if (ScreenRecordService.isCurrentlyRecording) {
                RecordingController.stop(this)
                updateStatus()
            } else {
                beginRecordingFlow()
            }
        }

        addTileButton.setOnClickListener { requestAddQuickSettingsTile() }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, ControlPanelActivity::class.java))
        }
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val recording = ScreenRecordService.isCurrentlyRecording
        statusText.text = if (recording) "Kayıt devam ediyor" else "Hazır"
        recordButton.text = if (recording) "KAYDI DURDUR" else "KAYDI BAŞLAT"
    }

    private fun beginRecordingFlow() {
        val settings = RecordingPreferences.load(this)
        val needsMicrophone = settings.audioMode == AudioMode.MICROPHONE ||
            settings.audioMode == AudioMode.MICROPHONE_AND_MEDIA

        if (needsMicrophone && ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_MICROPHONE
            )
            return
        }

        requestRecordingPermission()
    }

    private fun requestRecordingPermission() {
        startActivityForResult(
            projectionManager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        )
    }

    private fun requestAddQuickSettingsTile() {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "Bu özellik Android 13 ve üzeri için kullanılabilir.", Toast.LENGTH_LONG).show()
            return
        }

        val statusBarManager = getSystemService(StatusBarManager::class.java)
        val componentName = ComponentName(this, RecordingTileService::class.java)
        val icon = Icon.createWithResource(this, R.drawable.ic_screen_record)

        statusBarManager.requestAddTileService(componentName, "Ekran Kaydı", icon, mainExecutor) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                    "Ekran Kaydı kontrol paneline eklendi."
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                    "Ekran Kaydı zaten kontrol panelinde."
                else -> "Kontrol paneline ekleme tamamlanmadı."
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_MICROPHONE) return

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestRecordingPermission()
        } else {
            statusText.text = "Mikrofon izni verilmedi"
            Toast.makeText(this, "Mikrofon izni verilmedi.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MEDIA_PROJECTION) return

        if (resultCode != RESULT_OK || data == null) {
            statusText.text = "Kayıt izni verilmedi"
            Toast.makeText(this, "Ekran kaydı izni verilmedi.", Toast.LENGTH_SHORT).show()
            return
        }

        val countdownIntent = Intent(this, CountdownActivity::class.java).apply {
            putExtra(CountdownActivity.EXTRA_RESULT_CODE, resultCode)
            putExtra(CountdownActivity.EXTRA_DATA, data)
        }
        startActivity(countdownIntent)
    }
}
