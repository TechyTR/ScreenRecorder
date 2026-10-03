package com.nevruz.videor

import android.app.Activity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.Window
import android.widget.Button
import android.widget.TextView

class CountdownActivity : Activity() {

    private lateinit var countdownText: TextView
    private lateinit var skipButton: Button

    private var finished = false

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

        window.setGravity(Gravity.CENTER)

        countdownText =
            findViewById(R.id.countdownText)

        skipButton =
            findViewById(R.id.skipButton)

        skipButton.setOnClickListener {
            finishCountdown()
        }

        startCountdown()
    }

    private fun startCountdown() {

        object : CountDownTimer(
            3000,
            1000
        ) {

            override fun onTick(
                millisUntilFinished: Long
            ) {

                val seconds =
                    (millisUntilFinished + 999) / 1000

                countdownText.text =
                    seconds.toString()
            }

            override fun onFinish() {
                finishCountdown()
            }

        }.start()
    }

    private fun finishCountdown() {

        if (finished) return

        finished = true

        setResult(
            RESULT_OK
        )

        finish()
    }
}
