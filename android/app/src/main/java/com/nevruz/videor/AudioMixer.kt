package com.nevruz.videor

object AudioMixer {

    fun mix(
        media: ShortArray?,
        mediaLength: Int,
        microphone: ShortArray?,
        microphoneLength: Int,
        output: ShortArray
    ) {

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

            output[i] =
                (
                    mediaSample +
                            microphoneSample
                    )
                    .coerceIn(
                        Short.MIN_VALUE.toInt(),
                        Short.MAX_VALUE.toInt()
                    )
                    .toShort()
        }
    }

    fun copy(
        source: ShortArray,
        sourceLength: Int,
        output: ShortArray
    ) {

        val count =
            minOf(
                sourceLength,
                output.size
            )

        if (count > 0) {

            System.arraycopy(
                source,
                0,
                output,
                0,
                count
            )
        }

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
