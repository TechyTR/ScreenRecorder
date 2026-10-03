package com.nevruz.videor

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface

class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val fps: Int
) {

    private val codec =
        MediaCodec.createEncoderByType(
            MediaFormat.MIMETYPE_VIDEO_AVC
        )

    lateinit var inputSurface: Surface
        private set

    fun start() {

        val format =
            MediaFormat.createVideoFormat(
                MediaFormat.MIMETYPE_VIDEO_AVC,
                width,
                height
            )

        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.COLOR_FormatSurface
        )

        format.setInteger(
            MediaFormat.KEY_FRAME_RATE,
            fps
        )

        format.setInteger(
            MediaFormat.KEY_BIT_RATE,
            calculateBitrate()
        )

        format.setInteger(
            MediaFormat.KEY_I_FRAME_INTERVAL,
            1
        )

        codec.configure(
            format,
            null,
            null,
            MediaCodec.CONFIGURE_FLAG_ENCODE
        )

        inputSurface =
            codec.createInputSurface()

        codec.start()
    }

    fun codec(): MediaCodec {
        return codec
    }

    fun stop() {
        runCatching {
            codec.signalEndOfInputStream()
        }

        runCatching {
            codec.stop()
        }

        runCatching {
            codec.release()
        }
    }

    private fun calculateBitrate(): Int {

        val pixels =
            width.toLong() *
                    height.toLong()

        val fpsMultiplier =
            fps.toLong()

        val calculated =
            pixels *
                    fpsMultiplier *
                    2L

        return calculated
            .coerceIn(
                8_000_000L,
                80_000_000L
            )
            .toInt()
    }

    private object MediaCodecInfo {
        const val Color_FormatSurface = 2130708361
    }
}
