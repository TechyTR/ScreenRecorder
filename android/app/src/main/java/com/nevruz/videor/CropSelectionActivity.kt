package com.nevruz.videor

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout

class CropSelectionActivity : Activity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        window.setStatusBarColor(
            android.graphics.Color.TRANSPARENT
        )

        window.setNavigationBarColor(
            android.graphics.Color.TRANSPARENT
        )

        val root =
            FrameLayout(this)

        root.setBackgroundColor(
            android.graphics.Color.TRANSPARENT
        )

        val selectionView =
            CropSelectionView(this)

        root.addView(
            selectionView,
            FrameLayout.LayoutParams(
                -1,
                -1
            )
        )

        val button =
            Button(this).apply {

                text = "KIRPILAN ALANI KAYDET"

                setOnClickListener {

                    RecordingPreferences.save(
                        this@CropSelectionActivity,
                        RecordingPreferences.load(
                            this@CropSelectionActivity
                        ).copy(
                            screenMode =
                                ScreenMode.CROPPED
                        )
                    )

                    finish()
                }
            }

        val params =
            FrameLayout.LayoutParams(
                -2,
                -2
            ).apply {

                gravity =
                    Gravity.BOTTOM or
                            Gravity.CENTER_HORIZONTAL

                bottomMargin = 70
            }

        root.addView(
            button,
            params
        )

        setContentView(root)
    }
}
