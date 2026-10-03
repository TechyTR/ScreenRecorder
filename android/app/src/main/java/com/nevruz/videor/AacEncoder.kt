package com.nevruz.videor

import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer

class AacEncoder(
    private val sampleRate: Int = 48_000,
    private val channelCount: Int = 2,
    private val bitrate: Int = 192_000
) {

    companion object {
        private const val MIME =
            MediaFormat.MIMETYPE_AUDIO_AAC

        private const val TIMEOUT_US = 10_000L
    }

    private val codec: MediaCodec =
        MediaCodec.createEncoderByType(MIME)

    private var started = false

    fun start() {

        val format =
            MediaFormat.createAudioFormat(
                MIME,
                sampleRate,
                channelCount
            )

        format.setInteger(
            MediaFormat.KEY_AAC_PROFILE,
            2
        )

        format.setInteger(
            MediaFormat.KEY_BIT_RATE,
            bitrate
        )

        format.setInteger(
            MediaFormat.KEY_MAX_INPUT_SIZE,
            16 * 1024
        )

        codec.configure(
            format,
            null,
            null,
            MediaCodec.CONFIGURE_FLAG_ENCODE
        )

        codec.start()

        started = true
    }

    fun encode(
        pcm: ShortArray,
        size: Int,
        presentationTimeUs: Long
    ): List<AudioPacket> {

        if (!started) {
            return emptyList()
        }

        val packets =
            ArrayList<AudioPacket>()

        val inputIndex =
            codec.dequeueInputBuffer(
                TIMEOUT_US
            )

        if (inputIndex >= 0) {

            val inputBuffer =
                codec.getInputBuffer(
                    inputIndex
                )

            if (inputBuffer != null) {

                inputBuffer.clear()

                val byteCount =
                    size * 2

                for (i in 0 until size) {

                    val sample =
                        pcm[i]

                    inputBuffer.put(
                        (sample.toInt() and 0xFF)
                            .toByte()
                    )

                    inputBuffer.put(
                        ((sample.toInt() shr 8) and 0xFF)
                            .toByte()
                    )
                }

                codec.queueInputBuffer(
                    inputIndex,
                    0,
                    byteCount,
                    presentationTimeUs,
                    0
                )
            }
        }

        drain(packets)

        return packets
    }

    fun finish(): List<AudioPacket> {

        if (!started) {
            return emptyList()
        }

        val packets =
            ArrayList<AudioPacket>()

        val inputIndex =
            codec.dequeueInputBuffer(
                TIMEOUT_US
            )

        if (inputIndex >= 0) {

            codec.queueInputBuffer(
                inputIndex,
                0,
                0,
                0,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }

        var finished = false

        while (!finished) {

            val info =
                MediaCodec.BufferInfo()

            val outputIndex =
                codec.dequeueOutputBuffer(
                    info,
                    TIMEOUT_US
                )

            when {

                outputIndex >= 0 -> {

                    val output =
                        codec.getOutputBuffer(
                            outputIndex
                        )

                    if (
                        output != null &&
                        info.size > 0
                    ) {

                        val data =
                            ByteArray(info.size)

                        output.position(
                            info.offset
                        )

                        output.limit(
                            info.offset +
                                    info.size
                        )

                        output.get(data)

                        packets.add(
                            AudioPacket(
                                data = data,
                                info =
                                    MediaCodec.BufferInfo().also {
                                        it.set(
                                            0,
                                            data.size,
                                            info.presentationTimeUs,
                                            info.flags
                                        )
                                    }
                            )
                        )
                    }

                    finished =
                        (
                            info.flags and
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        ) != 0

                    codec.releaseOutputBuffer(
                        outputIndex,
                        false
                    )
                }

                outputIndex ==
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // Continue waiting for EOS.
                }
            }
        }

        return packets
    }

    fun getOutputFormat(): MediaFormat {
        return codec.outputFormat
    }

    private fun drain(
        packets: MutableList<AudioPacket>
    ) {

        while (true) {

            val info =
                MediaCodec.BufferInfo()

            val outputIndex =
                codec.dequeueOutputBuffer(
                    info,
                    TIMEOUT_US
                )

            if (
                outputIndex ==
                MediaCodec.INFO_TRY_AGAIN_LATER
            ) {
                return
            }

            if (
                outputIndex ==
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
            ) {
                continue
            }

            if (outputIndex < 0) {
                return
            }

            val output =
                codec.getOutputBuffer(
                    outputIndex
                )

            if (
                output != null &&
                info.size > 0
            ) {

                val data =
                    ByteArray(info.size)

                output.position(
                    info.offset
                )

                output.limit(
                    info.offset +
                            info.size
                )

                output.get(data)

                val copyInfo =
                    MediaCodec.BufferInfo()

                copyInfo.set(
                    0,
                    data.size,
                    info.presentationTimeUs,
                    info.flags
                )

                packets.add(
                    AudioPacket(
                        data = data,
                        info = copyInfo
                    )
                )
            }

            codec.releaseOutputBuffer(
                outputIndex,
                false
            )
        }
    }

    fun release() {

        if (started) {
            runCatching {
                codec.stop()
            }
        }

        codec.release()

        started = false
    }
}

data class AudioPacket(
    val data: ByteArray,
    val info: MediaCodec.BufferInfo
)
