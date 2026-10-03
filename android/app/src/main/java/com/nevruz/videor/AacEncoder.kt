package com.nevruz.videor

import android.media.MediaCodec
import android.media.MediaFormat

class AacEncoder(
    private val sampleRate: Int = SAMPLE_RATE,
    private val channelCount: Int = CHANNEL_COUNT,
    private val bitrate: Int = 192_000
) {

    companion object {

        const val SAMPLE_RATE = 48_000
        const val CHANNEL_COUNT = 2

        private const val TIMEOUT_US =
            10_000L
    }

    private val codec =
        MediaCodec.createEncoderByType(
            MediaFormat.MIMETYPE_AUDIO_AAC
        )

    private var started = false

    private var outputFormat: MediaFormat? = null

    fun start() {

        val format =
            MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC,
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

        if (!started || size <= 0) {
            return emptyList()
        }

        val packets =
            ArrayList<AudioPacket>()

        val inputIndex =
            codec.dequeueInputBuffer(
                TIMEOUT_US
            )

        if (inputIndex >= 0) {

            val input =
                codec.getInputBuffer(
                    inputIndex
                )

            if (input != null) {

                input.clear()

                val byteCount =
                    size * 2

                for (i in 0 until size) {

                    val sample =
                        pcm[i].toInt()

                    input.put(
                        (sample and 0xFF).toByte()
                    )

                    input.put(
                        ((sample shr 8) and 0xFF).toByte()
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

        var eosQueued = false

        while (!eosQueued) {

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

                eosQueued = true
            }
        }

        var eosReceived = false

        while (!eosReceived) {

            val info =
                MediaCodec.BufferInfo()

            val index =
                codec.dequeueOutputBuffer(
                    info,
                    TIMEOUT_US
                )

            when {

                index ==
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                    outputFormat =
                        codec.outputFormat
                }

                index >= 0 -> {

                    val buffer =
                        codec.getOutputBuffer(index)

                    if (
                        buffer != null &&
                        info.size > 0 &&
                        (
                            info.flags and
                                MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                        ) == 0
                    ) {

                        packets.add(
                            createPacket(
                                buffer,
                                info
                            )
                        )
                    }

                    eosReceived =
                        (
                            info.flags and
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        ) != 0

                    codec.releaseOutputBuffer(
                        index,
                        false
                    )
                }
            }
        }

        return packets
    }

    fun getOutputFormat(): MediaFormat? {
        return outputFormat
    }

    private fun drain(
        packets: MutableList<AudioPacket>
    ) {

        while (true) {

            val info =
                MediaCodec.BufferInfo()

            val index =
                codec.dequeueOutputBuffer(
                    info,
                    TIMEOUT_US
                )

            when {

                index ==
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    return
                }

                index ==
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                    outputFormat =
                        codec.outputFormat
                }

                index >= 0 -> {

                    val buffer =
                        codec.getOutputBuffer(index)

                    if (
                        buffer != null &&
                        info.size > 0 &&
                        (
                            info.flags and
                                MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                        ) == 0
                    ) {

                        packets.add(
                            createPacket(
                                buffer,
                                info
                            )
                        )
                    }

                    codec.releaseOutputBuffer(
                        index,
                        false
                    )
                }
            }
        }
    }

    private fun createPacket(
        buffer: java.nio.ByteBuffer,
        info: MediaCodec.BufferInfo
    ): AudioPacket {

        val data =
            ByteArray(info.size)

        buffer.position(info.offset)

        buffer.limit(
            info.offset +
                    info.size
        )

        buffer.get(data)

        val copyInfo =
            MediaCodec.BufferInfo()

        copyInfo.set(
            0,
            data.size,
            info.presentationTimeUs,
            info.flags
        )

        return AudioPacket(
            data = data,
            info = copyInfo
        )
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
        outputFormat = null
    }
}

data class AudioPacket(
    val data: ByteArray,
    val info: MediaCodec.BufferInfo
)
