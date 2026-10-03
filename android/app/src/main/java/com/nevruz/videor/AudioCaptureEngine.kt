package com.nevruz.videor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection

class AudioCaptureEngine(
    private val context: Context,
    private val mediaProjection: MediaProjection
) {

    private var audioRecord: AudioRecord? = null

    fun start(): AudioRecord? {
        return try {
            val config =
                AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(
                        AudioAttributes.USAGE_MEDIA
                    )
                    .addMatchingUsage(
                        AudioAttributes.USAGE_GAME
                    )
                    .build()

            val audioFormat =
                AudioFormat.Builder()
                    .setEncoding(
                        AudioFormat.ENCODING_PCM_16BIT
                    )
                    .setSampleRate(44100)
                    .setChannelMask(
                        AudioFormat.CHANNEL_IN_STEREO
                    )
                    .build()

            val bufferSize =
                AudioRecord.getMinBufferSize(
                    44100,
                    AudioFormat.CHANNEL_IN_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(8192)

            val record =
                AudioRecord.Builder()
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()

            audioRecord = record

            record.startRecording()

            record
        } catch (e: Exception) {
            audioRecord?.release()
            audioRecord = null
            null
        }
    }

    fun stop() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) {
        }

        audioRecord?.release()
        audioRecord = null
    }

    fun getAudioRecord(): AudioRecord? {
        return audioRecord
    }
}
