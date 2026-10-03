package com.nevruz.videor

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer

class AudioMuxer(
    private val muxer: MediaMuxer
) {

    private var audioTrack =
        -1

    fun addTrack(
        format: MediaFormat
    ) {

        if (audioTrack != -1) {
            return
        }

        audioTrack =
            muxer.addTrack(format)
    }

    fun write(
        packet: AudioPacket
    ) {

        if (audioTrack == -1) {
            return
        }

        val buffer =
            ByteBuffer.wrap(
                packet.data
            )

        muxer.writeSampleData(
            audioTrack,
            buffer,
            packet.info
        )
    }

    fun isReady(): Boolean {
        return audioTrack != -1
    }
}
