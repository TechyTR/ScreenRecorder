package com.nevruz.videor

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

class VideoMuxer(
    private val muxer: MediaMuxer,
    private val audioEnabled: Boolean
) {

    private var videoTrack = -1
    private var audioTrack = -1

    private var started = false
    private var stopped = false

    private val pendingVideo =
        ArrayList<EncodedVideoPacket>()

    private val pendingAudio =
        ArrayList<AudioPacket>()

    @Synchronized
    fun addVideoTrack(
        format: MediaFormat
    ) {

        if (videoTrack != -1) {
            return
        }

        videoTrack =
            muxer.addTrack(format)

        tryStart()
    }

    @Synchronized
    fun addAudioTrack(
        format: MediaFormat
    ) {

        if (audioTrack != -1) {
            return
        }

        audioTrack =
            muxer.addTrack(format)

        tryStart()
    }

    @Synchronized
    fun writeVideo(
        data: ByteBuffer,
        info: MediaCodec.BufferInfo
    ) {

        if (stopped) {
            return
        }

        val copy =
            ByteArray(info.size)

        val oldPosition =
            data.position()

        val oldLimit =
            data.limit()

        data.position(info.offset)

        data.limit(
            info.offset +
                    info.size
        )

        data.get(copy)

        data.position(oldPosition)
        data.limit(oldLimit)

        val packet =
            EncodedVideoPacket(
                data = copy,
                info = copyInfo(info)
            )

        if (!started) {
            pendingVideo.add(packet)
            return
        }

        writeVideoInternal(packet)
    }

    @Synchronized
    fun writeAudio(
        packet: AudioPacket
    ) {

        if (stopped) {
            return
        }

        if (!started) {
            pendingAudio.add(packet)
            return
        }

        writeAudioInternal(packet)
    }

    @Synchronized
    fun isStarted(): Boolean {
        return started
    }

    @Synchronized
    fun stop() {

        if (stopped) {
            return
        }

        stopped = true

        if (started) {

            runCatching {
                muxer.stop()
            }
        }

        runCatching {
            muxer.release()
        }

        started = false
    }

    private fun tryStart() {

        if (started) {
            return
        }

        if (videoTrack == -1) {
            return
        }

        if (
            audioEnabled &&
            audioTrack == -1
        ) {
            return
        }

        muxer.start()

        started = true

        pendingVideo
            .sortedBy {
                it.info.presentationTimeUs
            }
            .forEach {
                writeVideoInternal(it)
            }

        pendingAudio
            .sortedBy {
                it.info.presentationTimeUs
            }
            .forEach {
                writeAudioInternal(it)
            }

        pendingVideo.clear()
        pendingAudio.clear()
    }

    private fun writeVideoInternal(
        packet: EncodedVideoPacket
    ) {

        if (videoTrack == -1) {
            return
        }

        muxer.writeSampleData(
            videoTrack,
            ByteBuffer.wrap(packet.data),
            packet.info
        )
    }

    private fun writeAudioInternal(
        packet: AudioPacket
    ) {

        if (audioTrack == -1) {
            return
        }

        muxer.writeSampleData(
            audioTrack,
            ByteBuffer.wrap(packet.data),
            packet.info
        )
    }

    private fun copyInfo(
        info: MediaCodec.BufferInfo
    ): MediaCodec.BufferInfo {

        return MediaCodec.BufferInfo().also {
            it.set(
                0,
                info.size,
                info.presentationTimeUs,
                info.flags
            )
        }
    }

    private data class EncodedVideoPacket(
        val data: ByteArray,
        val info: MediaCodec.BufferInfo
    )
}
