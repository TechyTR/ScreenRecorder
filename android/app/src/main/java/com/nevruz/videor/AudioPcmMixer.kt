package com.nevruz.videor

object AudioPcmMixer {

    fun mix(
        microphone: ShortArray,
        microphoneSamples: Int,
        media: ShortArray,
        mediaSamples: Int,
        output: ShortArray
    ): Int {

        val count =
            minOf(
                microphoneSamples,
                mediaSamples,
                output.size
            )

        for (i in 0 until count) {

            val mic =
                microphone[i].toInt()

            val mediaSample =
                media[i].toInt()

            val mixed =
                mic + mediaSample

            output[i] =
                mixed.coerceIn(
                    Short.MIN_VALUE.toInt(),
                    Short.MAX_VALUE.toInt()
                ).toShort()
        }

        return count
    }

    fun copy(
        source: ShortArray,
        samples: Int,
        output: ShortArray
    ): Int {

        val count =
            minOf(
                samples,
                output.size
            )

        System.arraycopy(
            source,
            0,
            output,
            0,
            count
        )

        return count
    }
}
