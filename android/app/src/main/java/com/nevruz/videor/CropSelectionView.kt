package com.nevruz.videor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class CropSelectionView(
    context: Context
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val selection = RectF()

    private var startX = 0f
    private var startY = 0f

    private var selecting = false

    init {
        setBackgroundColor(Color.TRANSPARENT)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 5f
        paint.color = Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (selection.width() > 0f &&
            selection.height() > 0f
        ) {
            canvas.drawRect(
                selection,
                paint
            )
        }
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y

                selection.set(
                    startX,
                    startY,
                    startX,
                    startY
                )

                selecting = true

                invalidate()

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                if (!selecting) {
                    return true
                }

                selection.set(
                    minOf(startX, event.x),
                    minOf(startY, event.y),
                    maxOf(startX, event.x),
                    maxOf(startY, event.y)
                )

                invalidate()

                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                selecting = false

                invalidate()

                return true
            }
        }

        return true
    }

    fun getSelection(): RectF {
        return RectF(selection)
    }
}
