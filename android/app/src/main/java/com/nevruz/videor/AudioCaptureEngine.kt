package com.nevruz.videor

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.AudioPlaybackCaptureConfiguration
import android.os.Build

class AudioCaptureEngine(
    private val context: Context,
    private val mode: AudioMode,
    private val projection: MediaProjection?
) {

    companion object {
        const val SAMPLE_RATE = 48000
        const val CHANNEL_COUNT = 2

        private const val CHANNEL_MASK =
            AudioFormat.CHANNEL_IN_STEREO

        private const val ENCODING =
            AudioFormat.ENCODING_PCM_16BIT
    }

    private var playbackRecord: AudioRecord? = null
    private var microphoneRecord: AudioRecord? = null

    private var running = false

    fun start() {

        if (mode == AudioMode.OFF) {
            return
        }

        if (mode == AudioMode.MEDIA ||
            mode == AudioMode.MICROPHONE_AND_MEDIA
        ) {
            createPlaybackRecorder()
        }

        if (mode == AudioMode.MICROPHONE ||
            mode == AudioMode.MICROPHONE_AND_MEDIA
        ) {
            createMicrophoneRecorder()
        }

        running = true

        playbackRecord?.startRecording()
        microphoneRecord?.startRecording()
    }

    private fun createPlaybackRecorder() {

        if (Build.VERSION.SDK_INT < 29) {
            return
        }

        val mediaProjection =
            projection ?: return

        val config =
            AudioPlaybackCaptureConfiguration
                .Builder(mediaProjection)
                .addMatchingUsage(
                    android.media.AudioAttributes
                        .USAGE_MEDIA
                )
                .addMatchingUsage(
                    android.media.AudioAttributes
                        .USAGE_GAME
                )
                .build()

        val minBuffer =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_MASK,
                ENCODING
            )

        playbackRecord =
            AudioRecord.Builder()
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_MASK)
                        .build()
                )
                .setBufferSizeInBytes(
                    minBuffer * 2
                )
                .setAudioPlaybackCaptureConfig(
                    config
                )
                .build()
    }

    private fun createMicrophoneRecorder() {

        val minBuffer =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_MASK,
                ENCODING
            )

        microphoneRecord =
            AudioRecord.Builder()
                .setAudioSource(
                    android.media.MediaRecorder
                        .AudioSource
                        .MIC
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_MASK)
                        .build()
                )
                .setBufferSizeInBytes(
                    minBuffer * 2
                )
                .build()
    }

    fun readPlayback(
        buffer: ShortArray
    ): Int {

        return playbackRecord?.read(
            buffer,
            0,
            buffer.size
        ) ?: 0
    }

    fun readMicrophone(
        buffer: ShortArray
    ): Int {

        return microphoneRecord?.read(
            buffer,
            0,
            buffer.size
        ) ?: 0
    }

    fun stop() {

        running = false

        try {
            playbackRecord?.stop()
        } catch (_: Exception) {
        }

        try {
            microphoneRecord?.stop()
        } catch (_: Exception) {
        }

        playbackRecord?.release()
        microphoneRecord?.release()

        playbackRecord = null
        microphoneRecord = null
    }
}
