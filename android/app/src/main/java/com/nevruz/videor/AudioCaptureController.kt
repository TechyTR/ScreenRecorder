package com.nevruz.videor

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection

class AudioCaptureController(
    private val context: Context,
    private val projection: MediaProjection,
    private val mode: AudioMode
) {

    companion object {

        const val SAMPLE_RATE = 48_000
        const val CHANNEL_COUNT = 2

        private const val CHANNEL_MASK =
            AudioFormat.CHANNEL_IN_STEREO

        private const val ENCODING =
            AudioFormat.ENCODING_PCM_16BIT
    }

    private var mediaRecorder: AudioRecord? = null
    private var microphoneRecorder: AudioRecord? = null

    private var running = false

    fun start() {

        if (mode == AudioMode.OFF) {
            return
        }

        mediaRecorder =
            if (
                mode == AudioMode.MEDIA ||
                mode == AudioMode.MICROPHONE_AND_MEDIA
            ) {
                createMediaRecorder()
            } else {
                null
            }

        microphoneRecorder =
            if (
                mode == AudioMode.MICROPHONE ||
                mode == AudioMode.MICROPHONE_AND_MEDIA
            ) {
                createMicrophoneRecorder()
            } else {
                null
            }

        mediaRecorder?.startRecording()
        microphoneRecorder?.startRecording()

        running = true
    }

    fun readMedia(
        buffer: ShortArray
    ): Int {

        if (!running) {
            return 0
        }

        return mediaRecorder?.read(
            buffer,
            0,
            buffer.size
        ) ?: 0
    }

    fun readMicrophone(
        buffer: ShortArray
    ): Int {

        if (!running) {
            return 0
        }

        return microphoneRecorder?.read(
            buffer,
            0,
            buffer.size
        ) ?: 0
    }

    fun stop() {

        running = false

        runCatching {
            mediaRecorder?.stop()
        }

        runCatching {
            microphoneRecorder?.stop()
        }

        runCatching {
            mediaRecorder?.release()
        }

        runCatching {
            microphoneRecorder?.release()
        }

        mediaRecorder = null
        microphoneRecorder = null
    }

    private fun createMediaRecorder(): AudioRecord {

        val playbackConfig =
            AudioPlaybackCaptureConfiguration
                .Builder(projection)
                .addMatchingUsage(
                    AudioAttributes.USAGE_MEDIA
                )
                .addMatchingUsage(
                    AudioAttributes.USAGE_GAME
                )
                .build()

        val format =
            AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_MASK)
                .build()

        val minimum =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_MASK,
                ENCODING
            )

        return AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(
                maxOf(
                    minimum * 2,
                    SAMPLE_RATE
                )
            )
            .setAudioPlaybackCaptureConfig(
                playbackConfig
            )
            .build()
    }

    private fun createMicrophoneRecorder(): AudioRecord {

        val format =
            AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_MASK)
                .build()

        val minimum =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_MASK,
                ENCODING
            )

        return AudioRecord.Builder()
            .setAudioSource(
                MediaRecorder.AudioSource.MIC
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(
                maxOf(
                    minimum * 2,
                    SAMPLE_RATE
                )
            )
            .build()
    }
}
