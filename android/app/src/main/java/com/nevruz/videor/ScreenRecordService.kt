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
import android.os.Handler
import android.os.Looper
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
    }

    private var projection:
        MediaProjection? = null

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                if (recording) {
                    Handler(Looper.getMainLooper()).post {
                        stopRecording()
                    }
                }
            }
        }

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

            startForegroundCompat(
                audioEnabled
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

            projection!!.registerCallback(
                projectionCallback,
                Handler(Looper.getMainLooper())
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

            videoEncoder =
                VideoEncoder(
                    width,
                    height,
                    fps
                )

            videoEncoder!!.start()

            virtualDisplay =
                projection!!.createVirtualDisplay(
                    "Stellar VideoR",
                    width,
                    height,
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

            Log.e("StellarVideoR", "Ekran kaydı başlatılamadı", error)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    "Kayıt başlatılamadı: ${error.localizedMessage ?: "bilinmeyen hata"}",
                    Toast.LENGTH_LONG
                ).show()
            }

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
            projection?.unregisterCallback(projectionCallback)
        }

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

        runCatching {
            muxer?.stop()
        }

        val savedOutput =
            output

        if (savedOutput != null) {

            runCatching {
                RecordingOutput.finish(
                    this,
                    savedOutput
                )
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
