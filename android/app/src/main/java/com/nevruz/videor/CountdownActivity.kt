package com.nevruz.videor

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.TextView

class CountdownActivity : Activity() {

    private lateinit var countdownText: TextView
    private lateinit var skipButton: Button

    private var finished = false

    private val handler =
        android.os.Handler(
            android.os.Looper.getMainLooper()
        )

    private var remaining = 3

    private val countdownRunnable =
        object : Runnable {

            override fun run() {

                if (finished) {
                    return
                }

                if (remaining <= 0) {
                    finishCountdown()
                    return
                }

                countdownText.text =
                    remaining.toString()

                remaining--

                handler.postDelayed(
                    this,
                    1000L
                )
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(
            Window.FEATURE_NO_TITLE
        )

        setContentView(
            R.layout.activity_countdown
        )

        window.setGravity(
            Gravity.CENTER
        )

        countdownText =
            findViewById(
                R.id.countdownText
            )

        skipButton =
            findViewById(
                R.id.skipButton
            )

        skipButton.setOnClickListener {
            finishCountdown()
        }

        handler.post(
            countdownRunnable
        )
    }

    private fun finishCountdown() {

        if (finished) {
            return
        }

        finished = true

        handler.removeCallbacks(
            countdownRunnable
        )

        startRecordingService()

        finish()
    }

    private fun startRecordingService() {

        val resultCode =
            intent.getIntExtra(
                EXTRA_RESULT_CODE,
                -1
            )

        val projectionData =
            if (android.os.Build.VERSION.SDK_INT >= 33) {

                intent.getParcelableExtra(
                    EXTRA_DATA,
                    Intent::class.java
                )

            } else {

                @Suppress("DEPRECATION")
                intent.getParcelableExtra(
                    EXTRA_DATA
                )
            }

        if (
            resultCode == -1 ||
            projectionData == null
        ) {
            return
        }

        val settings =
            RecordingPreferences.load(this)

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
                    projectionData
                )

                putExtra(
                    ScreenRecordService.EXTRA_WIDTH,
                    settings.width
                )

                putExtra(
                    ScreenRecordService.EXTRA_HEIGHT,
                    settings.height
                )

                putExtra(
                    ScreenRecordService.EXTRA_FPS,
                    settings.fps
                )
            }

        if (
            android.os.Build.VERSION.SDK_INT >= 26
        ) {

            startForegroundService(
                serviceIntent
            )

        } else {

            startService(
                serviceIntent
            )
        }
    }

    override fun onDestroy() {

        handler.removeCallbacks(
            countdownRunnable
        )

        super.onDestroy()
    }

    companion object {

        const val EXTRA_RESULT_CODE =
            "extra_result_code"

        const val EXTRA_DATA =
            "extra_data"
    }
}
