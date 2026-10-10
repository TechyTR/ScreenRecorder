package com.nevruz.videor

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface

class VideoEncoder(
    val width: Int,
    val height: Int,
    private val fps: Int
) {

    companion object {
        private const val MIME =
            MediaFormat.MIMETYPE_VIDEO_AVC
    }

    private val codec =
        MediaCodec.createEncoderByType(MIME)

    lateinit var inputSurface: Surface
        private set

    private var started = false

    fun start() {

        val format =
            MediaFormat.createVideoFormat(
                MIME,
                width,
                height
            )

        format.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
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

        started = true
    }

    fun codec(): MediaCodec {
        return codec
    }

    fun signalEndOfInputStream() {

        if (!started) {
            return
        }

        runCatching {
            codec.signalEndOfInputStream()
        }
    }

    fun release() {
        if (started) {
            runCatching {
                codec.stop()
            }
        }

        runCatching {
            codec.release()
        }

        started = false
    }

    private fun calculateBitrate(): Int {

        val pixels =
            width.toLong() *
                    height.toLong()

        val calculated =
            pixels *
                    fps.toLong() *
                    0.45

        return calculated
            .toLong()
            .coerceIn(
                12_000_000L,
                120_000_000L
            )
            .toInt()
    }
}
