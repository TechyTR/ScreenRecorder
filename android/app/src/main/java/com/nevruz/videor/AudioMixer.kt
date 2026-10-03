package com.nevruz.videor

object AudioMixer {

    fun mix(
        media: ShortArray?,
        microphone: ShortArray?,
        output: ShortArray
    ) {

        val mediaLength =
            media?.size ?: 0

        val microphoneLength =
            microphone?.size ?: 0

        for (i in output.indices) {

            val mediaSample =
                if (
                    media != null &&
                    i < mediaLength
                ) {
                    media[i].toInt()
                } else {
                    0
                }

            val microphoneSample =
                if (
                    microphone != null &&
                    i < microphoneLength
                ) {
                    microphone[i].toInt()
                } else {
                    0
                }

            val mixed =
                mediaSample +
                        microphoneSample

            output[i] =
                mixed
                    .coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    )
                    .toShort()
        }
    }

    fun copy(
        source: ShortArray,
        output: ShortArray
    ) {

        val count =
            minOf(
                source.size,
                output.size
            )

        System.arraycopy(
            source,
            0,
            output,
            0,
            count
        )

        if (count < output.size) {
            java.util.Arrays.fill(
                output,
                count,
                output.size,
                0
            )
        }
    }
}
