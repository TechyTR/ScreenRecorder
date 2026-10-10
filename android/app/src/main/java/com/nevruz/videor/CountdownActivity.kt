package com.nevruz.videor

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class CountdownActivity : Activity() {

    private lateinit var countdownText: TextView
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var remaining = 3
    private var completed = false

    private val nextTick = object : Runnable {
        override fun run() {
            if (completed) return
            if (remaining == 0) {
                startRecordingService()
                return
            }
            showNumber(remaining)
            remaining--
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setGravity(Gravity.CENTER)
        window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        setContentView(R.layout.activity_countdown)
        countdownText = findViewById(R.id.countdownText)
        handler.post(nextTick)
    }

    private fun showNumber(number: Int) {
        countdownText.text = number.toString()
        countdownText.alpha = 0f
        countdownText.scaleX = 0.64f
        countdownText.scaleY = 0.64f
        countdownText.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(190L)
            .withEndAction {
                countdownText.animate()
                    .alpha(0.82f)
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(620L)
                    .start()
            }
            .start()
    }

    private fun startRecordingService() {
        if (completed) return
        completed = true
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode != RESULT_OK || data == null) {
            finish()
            return
        }
        val settings = RecordingPreferences.load(this)
        RecordingController.start(this, resultCode, data, settings.width, settings.height, settings.fps)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(nextTick)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
    }
}
