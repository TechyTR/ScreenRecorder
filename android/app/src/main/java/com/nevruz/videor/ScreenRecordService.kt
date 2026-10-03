package com.nevruz.videor

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Spinner

class ControlPanelActivity : Activity() {

    private lateinit var audioGroup: RadioGroup
    private lateinit var screenGroup: RadioGroup
    private lateinit var resolutionSpinner: Spinner

    private val resolutions =
        listOf(
            ResolutionOption(
                "HD+",
                1280,
                720,
                60
            ),
            ResolutionOption(
                "HD+",
                1280,
                720,
                120
            ),
            ResolutionOption(
                "FHD+",
                2340,
                1080,
                60
            ),
            ResolutionOption(
                "FHD+",
                2340,
                1080,
                120
            ),
            ResolutionOption(
                "QHD+",
                3120,
                1440,
                60
            ),
            ResolutionOption(
                "QHD+",
                3120,
                1440,
                120
            )
        )

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        if (
            ScreenRecordService
                .isCurrentlyRecording
        ) {
            finish()
            return
        }

        requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

        setContentView(
            R.layout.activity_control_panel
        )

        window.setGravity(
            Gravity.CENTER
        )

        window.setBackgroundDrawableResource(
            android.R.color.transparent
        )

        window.addFlags(
            WindowManager.LayoutParams.FLAG_DIM_BEHIND
        )

        window.attributes =
            window.attributes.apply {
                dimAmount = 0.28f
            }

        audioGroup =
            findViewById(
                R.id.audioGroup
            )

        screenGroup =
            findViewById(
                R.id.screenGroup
            )

        resolutionSpinner =
            findViewById(
                R.id.resolutionSpinner
            )

        setupResolutionSpinner()
        loadSettings()

        findViewById<Button>(
            R.id.startButton
        ).setOnClickListener {

            saveSettings()

            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                ).apply {
                    action =
                        MainActivity
                            .ACTION_START_FROM_PANEL
                }
            )

            finish()
        }
    }

    private fun setupResolutionSpinner() {

        val adapter =
            ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                resolutions
            )

        adapter.setDropDownViewResource(
            android.R.layout
                .simple_spinner_dropdown_item
        )

        resolutionSpinner.adapter =
            adapter
    }

    private fun loadSettings() {

        val settings =
            RecordingPreferences.load(this)

        when (settings.audioMode) {

            AudioMode.OFF ->
                audioGroup.check(
                    R.id.audioOff
                )

            AudioMode.MEDIA ->
                audioGroup.check(
                    R.id.audioMedia
                )

            AudioMode.MICROPHONE_AND_MEDIA ->
                audioGroup.check(
                    R.id.audioMicrophoneMedia
                )

            AudioMode.MICROPHONE ->
                audioGroup.check(
                    R.id.audioMicrophone
                )
        }

        when (settings.screenMode) {

            ScreenMode.FULL_SCREEN ->
                screenGroup.check(
                    R.id.screenFull
                )

            ScreenMode.CROPPED ->
                screenGroup.check(
                    R.id.screenCropped
                )
        }

        val index =
            resolutions.indexOfFirst {

                it.width ==
                        settings.width &&
                        it.height ==
                        settings.height &&
                        it.fps ==
                        settings.fps
            }

        if (index >= 0) {
            resolutionSpinner
                .setSelection(index)
        }
    }

    private fun saveSettings() {

        val audioMode =
            when (
                audioGroup.checkedRadioButtonId
            ) {

                R.id.audioMedia ->
                    AudioMode.MEDIA

                R.id.audioMicrophoneMedia ->
                    AudioMode
                        .MICROPHONE_AND_MEDIA

                R.id.audioMicrophone ->
                    AudioMode.MICROPHONE

                else ->
                    AudioMode.OFF
            }

        val screenMode =
            if (
                screenGroup.checkedRadioButtonId ==
                R.id.screenCropped
            ) {
                ScreenMode.CROPPED
            } else {
                ScreenMode.FULL_SCREEN
            }

        val resolution =
            resolutions[
                resolutionSpinner
                    .selectedItemPosition
            ]

        RecordingPreferences.save(
            this,
            RecordingSettings(
                audioMode =
                    audioMode,

                screenMode =
                    screenMode,

                width =
                    resolution.width,

                height =
                    resolution.height,

                fps =
                    resolution.fps
            )
        )
    }

    override fun onResume() {
        super.onResume()

        window.setLayout(
            (
                resources.displayMetrics
                    .widthPixels * 0.88f
            ).toInt(),
            WindowManager.LayoutParams
                .WRAP_CONTENT
        )
    }
}
