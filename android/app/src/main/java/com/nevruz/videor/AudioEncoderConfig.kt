package com.nevruz.videor

import android.media.MediaFormat

object AudioEncoderConfig {

    const val MIME_TYPE =
        "audio/mp4a-latm"

    const val SAMPLE_RATE =
        48000

    const val CHANNEL_COUNT =
        2

    const val BIT_RATE =
        192_000

    fun create(): MediaFormat {

        return MediaFormat.createAudioFormat(
            MIME_TYPE,
            SAMPLE_RATE,
            CHANNEL_COUNT
        ).apply {

            setInteger(
                MediaFormat.KEY_BIT_RATE,
                BIT_RATE
            )

            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                2
            )

            setInteger(
                MediaFormat.KEY_MAX_INPUT_SIZE,
                16384
            )
        }
    }
}
