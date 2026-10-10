package com.nevruz.videor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast

class ScreenRecordService : Service() {

    companion object {

        const val ACTION_START =
            "com.nevruz.videor.START"

        const val ACTION_STOP =
            "com.nevruz.videor.STOP"

        const val EXTRA_RESULT_CODE =
            "result_code"

        const val EXTRA_DATA =
            "data"

        const val EXTRA_WIDTH =
            "width"

        const val EXTRA_HEIGHT =
            "height"

        const val EXTRA_FPS =
            "fps"

        const val ACTION_STATE_CHANGED =
            "com.nevruz.videor.action.STATE_CHANGED"

        @Volatile
        var isCurrentlyRecording: Boolean = false
            private set

        private const val CHANNEL_ID =
            "screen_recording"

        private const val NOTIFICATION_ID =
            5001

        private const val TAG = "ScreenRecordService"
    }

    private var projection:
        MediaProjection? = null

    private var projectionCallback:
        MediaProjection.Callback? = null

    private var virtualDisplay:
        VirtualDisplay? = null

    private var videoEncoder:
        VideoEncoder? = null

    private var audioEncoder:
        AacEncoder? = null

    private var audioCapture:
        AudioCaptureController? = null

    private var muxer:
        VideoMuxer? = null

    private var output:
        RecordingOutput.Output? = null

    private var videoThread:
        Thread? = null

    private var audioThread:
        Thread? = null

    @Volatile
    private var recording = false

    private var audioMode =
        AudioMode.OFF

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {

                val resultCode =
                    intent.getIntExtra(
                        EXTRA_RESULT_CODE,
                        -1
                    )

                val data =
                    if (Build.VERSION.SDK_INT >= 33) {

                        intent.getParcelableExtra(
                            EXTRA_DATA,
                            Intent::class.java
                        )

                    } else {

                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(
                            EXTRA_DATA
                        )
                    }

                val width =
                    intent.getIntExtra(
                        EXTRA_WIDTH,
                        2340
                    )

                val height =
                    intent.getIntExtra(
                        EXTRA_HEIGHT,
                        1080
                    )

                val fps =
                    intent.getIntExtra(
                        EXTRA_FPS,
                        60
                    )

                if (
                    resultCode != -1 &&
                    data != null
                ) {

                    startRecording(
                        resultCode,
                        data,
                        width,
                        height,
                        fps
                    )
                }
            }

            ACTION_STOP -> {
                stopRecording()
            }
        }

        return START_NOT_STICKY
    }

    private fun startRecording(
        resultCode: Int,
        data: Intent,
        width: Int,
        height: Int,
        fps: Int
    ) {

        if (recording) {
            return
        }

        try {

            val settings =
                RecordingPreferences.load(this)

            audioMode =
                settings.audioMode

            val audioEnabled =
                audioMode != AudioMode.OFF

            val microphoneEnabled =
                audioMode == AudioMode.MICROPHONE ||
                    audioMode == AudioMode.MICROPHONE_AND_MEDIA

            startForegroundCompat(
                microphoneEnabled
            )

            val manager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            projection =
                manager.getMediaProjection(
                    resultCode,
                    data
                )
                    ?: error(
                        "MediaProjection oluşturulamadı."
                    )

            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (recording) {
                        stopRecording()
                    }
                }
            }

            projection!!.registerCallback(
                projectionCallback!!,
                android.os.Handler(mainLooper)
            )

            output =
                RecordingOutput.create(this)

            val mediaMuxer =
                MediaMuxer(
                    output!!.fileDescriptor,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )

            muxer =
                VideoMuxer(
                    mediaMuxer,
                    audioEnabled
                )

            videoEncoder = startVideoEncoder(width, height, fps)

            virtualDisplay =
                projection!!.createVirtualDisplay(
                    "Stellar VideoR",
                    videoEncoder!!.width,
                    videoEncoder!!.height,
                    resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    videoEncoder!!.inputSurface,
                    null,
                    null
                )

            /*
             * ÖNEMLİ:
             * Thread'leri başlatmadan önce true yapılmalı.
             */
            recording = true
            isCurrentlyRecording = true

            broadcastStateChanged()

            if (audioEnabled) {

                audioEncoder =
                    AacEncoder()

                audioEncoder!!.start()

                audioCapture =
                    AudioCaptureController(
                        this,
                        projection!!,
                        audioMode
                    )

                audioCapture!!.start()

                startAudioThread()
            }

            startVideoThread()

        } catch (error: Throwable) {

            Log.e(TAG, "Could not start screen recording", error)

            Toast.makeText(
                applicationContext,
                "Kayıt başlatılamadı: ${error.localizedMessage ?: error.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()

            recording = false
            isCurrentlyRecording = false

            broadcastStateChanged()

            cleanup()

            stopForeground(
                STOP_FOREGROUND_REMOVE
            )

            stopSelf()
        }
    }

    private fun startVideoEncoder(
        width: Int,
        height: Int,
        fps: Int
    ): VideoEncoder {

        val shortSide = minOf(width, height)
        val longSide = maxOf(width, height)
        val fallbackLong = ((longSide.toFloat() * 720f / shortSide).toInt() and 1.inv())
        val fallback = if (width <= height) {
            Triple(720, fallbackLong, 30)
        } else {
            Triple(fallbackLong, 720, 30)
        }
        val candidates = linkedSetOf(
            Triple(width, height, fps),
            Triple(width, height, 30),
            fallback
        )

        var lastError: Throwable? = null
        for ((candidateWidth, candidateHeight, candidateFps) in candidates) {
            val encoder = VideoEncoder(candidateWidth, candidateHeight, candidateFps)
            try {
                encoder.start()
                return encoder
            } catch (error: Throwable) {
                encoder.release()
                lastError = error
                Log.w(
                    TAG,
                    "Encoder profile $candidateWidth×$candidateHeight @ $candidateFps FPS failed",
                    error
                )
            }
        }

        throw lastError ?: IllegalStateException("Video encoder could not be created.")
    }

    private fun startVideoThread() {

        videoThread =
            Thread {

                val encoder =
                    videoEncoder
                        ?: return@Thread

                val codec =
                    encoder.codec()

                var eos = false

                while (!eos) {

                    val info =
                        MediaCodec.BufferInfo()

                    val index =
                        codec.dequeueOutputBuffer(
                            info,
                            10_000
                        )

                    when {

                        index ==
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                            muxer?.addVideoTrack(
                                codec.outputFormat
                            )
                        }

                        index >= 0 -> {

                            val buffer =
                                codec.getOutputBuffer(
                                    index
                                )

                            if (
                                buffer != null &&
                                info.size > 0 &&
                                (
                                    info.flags and
                                        MediaCodec.BUFFER_FLAG_CODEC_CONFIG
                                ) == 0
                            ) {

                                muxer?.writeVideo(
                                    buffer,
                                    info
                                )
                            }

                            eos =
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
            }

        videoThread!!.start()
    }

    private fun startAudioThread() {

        audioThread =
            Thread {

                val capture =
                    audioCapture
                        ?: return@Thread

                val encoder =
                    audioEncoder
                        ?: return@Thread

                val bufferSize =
                    2048

                val mediaBuffer =
                    ShortArray(bufferSize)

                val microphoneBuffer =
                    ShortArray(bufferSize)

                val mixedBuffer =
                    ShortArray(bufferSize)

                var totalFrames = 0L

                while (recording) {

                    val mediaRead =
                        if (
                            audioMode ==
                                AudioMode.MEDIA ||
                            audioMode ==
                                AudioMode.MICROPHONE_AND_MEDIA
                        ) {
                            capture.readMedia(
                                mediaBuffer
                            )
                        } else {
                            0
                        }

                    val microphoneRead =
                        if (
                            audioMode ==
                                AudioMode.MICROPHONE ||
                            audioMode ==
                                AudioMode.MICROPHONE_AND_MEDIA
                        ) {
                            capture.readMicrophone(
                                microphoneBuffer
                            )
                        } else {
                            0
                        }

                    val count =
                        maxOf(
                            mediaRead,
                            microphoneRead
                        )

                    if (count <= 0) {
                        continue
                    }

                    when (audioMode) {

                        AudioMode.MEDIA -> {

                            AudioMixer.copy(
                                mediaBuffer,
                                mediaRead,
                                mixedBuffer
                            )
                        }

                        AudioMode.MICROPHONE -> {

                            AudioMixer.copy(
                                microphoneBuffer,
                                microphoneRead,
                                mixedBuffer
                            )
                        }

                        AudioMode.MICROPHONE_AND_MEDIA -> {

                            AudioMixer.mix(
                                mediaBuffer,
                                mediaRead,
                                microphoneBuffer,
                                microphoneRead,
                                mixedBuffer
                            )
                        }

                        AudioMode.OFF -> {
                            continue
                        }
                    }

                    val timeUs =
                        totalFrames *
                            1_000_000L /
                            AacEncoder.SAMPLE_RATE

                    val packets =
                        encoder.encode(
                            mixedBuffer,
                            count,
                            timeUs
                        )

                    totalFrames +=
                        count.toLong() /
                            AacEncoder.CHANNEL_COUNT

                    val format =
                        encoder.getOutputFormat()

                    if (
                        format != null &&
                        muxer?.hasAudioTrack() != true
                    ) {

                        muxer?.addAudioTrack(
                            format
                        )
                    }

                    packets.forEach {
                        muxer?.writeAudio(it)
                    }
                }

                val finalPackets =
                    encoder.finish()

                val format =
                    encoder.getOutputFormat()

                if (
                    format != null &&
                    muxer?.hasAudioTrack() != true
                ) {

                    muxer?.addAudioTrack(
                        format
                    )
                }

                finalPackets.forEach {
                    muxer?.writeAudio(it)
                }
            }

        audioThread!!.start()
    }

    private fun stopRecording() {

        if (!recording) {
            return
        }

        recording = false
        isCurrentlyRecording = false

        broadcastStateChanged()

        runCatching {
            audioCapture?.stop()
        }

        runCatching {
            videoEncoder?.signalEndOfInputStream()
        }

        videoThread?.join(5000)
        audioThread?.join(5000)

        cleanup()

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    private fun cleanup() {

        runCatching {
            virtualDisplay?.release()
        }

        runCatching {
            projectionCallback?.let { projection?.unregisterCallback(it) }
        }

        projectionCallback = null

        runCatching {
            projection?.stop()
        }

        runCatching {
            videoEncoder?.release()
        }

        runCatching {
            audioEncoder?.release()
        }

        runCatching {
            audioCapture?.stop()
        }

        val hasMuxedOutput =
            muxer?.isStarted() == true

        runCatching {
            muxer?.stop()
        }

        val savedOutput =
            output

        if (savedOutput != null) {

            runCatching {
                if (hasMuxedOutput) {
                    RecordingOutput.finish(this, savedOutput)
                } else {
                    RecordingOutput.delete(this, savedOutput)
                }
            }
        }

        virtualDisplay = null
        projection = null
        videoEncoder = null
        audioEncoder = null
        audioCapture = null
        muxer = null
        output = null
        videoThread = null
        audioThread = null
    }

    private fun startForegroundCompat(
        microphoneEnabled: Boolean
    ) {

        val notification =
            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle(
                    "Ekran Kaydedici"
                )
                .setContentText(
                    "Ekran kaydediliyor"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_camera
                )
                .setOngoing(true)
                .build()

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
        ) {

            var serviceType =
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION

            if (
                Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.R &&
                microphoneEnabled
            ) {
                serviceType =
                    serviceType or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }

            startForeground(
                NOTIFICATION_ID,
                notification,
                serviceType
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT <
                Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Ekran Kaydı",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun broadcastStateChanged() {

        sendBroadcast(
            Intent(
                ACTION_STATE_CHANGED
            ).setPackage(packageName)
        )
    }

    override fun onDestroy() {

        recording = false
        isCurrentlyRecording = false

        broadcastStateChanged()

        cleanup()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
