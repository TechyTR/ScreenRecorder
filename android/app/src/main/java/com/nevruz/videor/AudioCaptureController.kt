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

        running = true

        if (
            mode == AudioMode.MEDIA ||
            mode == AudioMode.MICROPHONE_AND_MEDIA
        ) {
            mediaRecorder =
                createMediaRecorder()
        }

        if (
            mode == AudioMode.MICROPHONE ||
            mode == AudioMode.MICROPHONE_AND_MEDIA
        ) {
            microphoneRecorder =
                createMicrophoneRecorder()
        }

        mediaRecorder?.startRecording()
        microphoneRecorder?.startRecording()
    }

    fun readMedia(
        buffer: ShortArray
    ): Int {
        val recorder =
            mediaRecorder ?: return 0

        if (!running) {
            return 0
        }

        return recorder.read(
            buffer,
            0,
            buffer.size
        )
    }

    fun readMicrophone(
        buffer: ShortArray
    ): Int {
        val recorder =
            microphoneRecorder ?: return 0

        if (!running) {
            return 0
        }

        return recorder.read(
            buffer,
            0,
            buffer.size
        )
    }

    fun stop() {
        running = false

        runCatching {
            mediaRecorder?.stop()
        }

        runCatching {
            microphoneRecorder?.stop()
        }

        mediaRecorder?.release()
        microphoneRecorder?.release()

        mediaRecorder = null
        microphoneRecorder = null
    }

    private fun createMediaRecorder(): AudioRecord {

        val config =
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

        val minBuffer =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_MASK,
                ENCODING
            )

        return AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(
                maxOf(
                    minBuffer * 2,
                    SAMPLE_RATE
                )
            )
            .setAudioPlaybackCaptureConfig(config)
            .build()
    }

    private fun createMicrophoneRecorder(): AudioRecord {

        val format =
            AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_MASK)
                .build()

        val minBuffer =
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
                    minBuffer * 2,
                    SAMPLE_RATE
                )
            )
            .build()
    }
}
