package com.nevruz.videor

import android.app.Activity
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
    }

    private lateinit var statusText: TextView
    private lateinit var recordButton: Button
    private lateinit var addTileButton: Button
    private lateinit var settingsButton: Button

    private val recordingStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateStatus()
        }
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

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
            if (ScreenRecordService.isCurrentlyRecording) {
                RecordingController.stop(this)
                updateStatus()
            } else beginRecordingFlow()
        }
    }

    private fun beginRecordingFlow() {
        startActivity(Intent(this, ControlPanelActivity::class.java))
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

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ScreenRecordService.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(recordingStateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(recordingStateReceiver, filter)
        }
        updateStatus()
    }

    override fun onPause() {
        runCatching { unregisterReceiver(recordingStateReceiver) }
        super.onPause()
    }
}
