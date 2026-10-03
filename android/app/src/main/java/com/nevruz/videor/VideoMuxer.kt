package com.nevruz.videor

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

class VideoMuxer(
    private val muxer: MediaMuxer
) {

    private var videoTrack = -1
    private var audioTrack = -1

    private var started = false

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

    fun addAudioTrackIfNeeded(
        format: MediaFormat
    ) {
        addAudioTrack(format)
    }

    fun writeVideo(
        data: ByteBuffer,
        info: MediaCodec.BufferInfo
    ) {
        if (!started) {
            return
        }

        muxer.writeSampleData(
            videoTrack,
            data,
            info
        )
    }

    fun writeAudio(
        packet: AudioPacket
    ) {
        if (
            !started ||
            audioTrack == -1
        ) {
            return
        }

        val buffer =
            ByteBuffer.wrap(packet.data)

        muxer.writeSampleData(
            audioTrack,
            buffer,
            packet.info
        )
    }

    fun isStarted(): Boolean {
        return started
    }

    fun hasVideoTrack(): Boolean {
        return videoTrack != -1
    }

    fun hasAudioTrack(): Boolean {
        return audioTrack != -1
    }

    fun stop() {

        if (!started) {
            return
        }

        runCatching {
            muxer.stop()
        }

        runCatching {
            muxer.release()
        }

        started = false
    }

    private fun tryStart() {

        if (
            !started &&
            videoTrack != -1
        ) {
            muxer.start()
            started = true
        }
    }
}
