package com.nevruz.videor

import android.app.Activity
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

        setResult(
            RESULT_OK
        )

        finish()
    }

    override fun onDestroy() {

        handler.removeCallbacks(
            countdownRunnable
        )

        super.onDestroy()
    }
}
