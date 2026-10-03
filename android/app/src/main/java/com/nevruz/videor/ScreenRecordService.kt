package com.nevruz.videor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import java.io.File

class ScreenRecordService : Service() {

    companion object {

        private const val TAG = "StellarVideoR"

        const val ACTION_START =
            "com.nevruz.videor.action.START"

        const val ACTION_STOP =
            "com.nevruz.videor.action.STOP"

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

        private const val CHANNEL_ID =
            "stellar_videor_recording"

        private const val NOTIFICATION_ID =
            1001

        private const val MIME_TYPE =
            "video/avc"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null

    private var encoderInputSurface: android.view.Surface? = null

    private var recordingThread: Thread? = null

    @Volatile
    private var isRecording = false

    @Volatile
    private var stopRequested = false

    private var muxerStarted = false
    private var videoTrack = -1

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

                if (!isRecording) {

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
                        resultCode == -1 ||
                        data == null
                    ) {
                        stopSelf()
                        return START_NOT_STICKY
                    }

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

        try {

            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

            Log.d(
                TAG,
                "Starting: ${width}x${height} @ ${fps} FPS"
            )

            val encoderInfo =
                findEncoder(
                    width,
                    height,
                    fps
                )

            if (encoderInfo == null) {

                Log.e(
                    TAG,
                    "No compatible AVC encoder found"
                )

                stopSelf()
                return
            }

            Log.d(
                TAG,
                "Encoder: ${encoderInfo.name}"
            )

            val format =
                MediaFormat.createVideoFormat(
                    MIME_TYPE,
                    width,
                    height
                )

            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities
                    .COLOR_FormatSurface
            )

            format.setInteger(
                MediaFormat.KEY_BIT_RATE,
                calculateBitrate(
                    width,
                    height,
                    fps
                )
            )

            format.setInteger(
                MediaFormat.KEY_FRAME_RATE,
                fps
            )

            format.setInteger(
                MediaFormat.KEY_I_FRAME_INTERVAL,
                1
            )

            val codec =
                MediaCodec.createByCodecName(
                    encoderInfo.name
                )

            encoder = codec

            codec.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )

            encoderInputSurface =
                codec.createInputSurface()

            codec.start()

            val outputFile =
                createOutputFile()

            muxer =
                MediaMuxer(
                    outputFile.absolutePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )

            val projectionManager =
                getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            mediaProjection =
                projectionManager.getMediaProjection(
                    resultCode,
                    data
                )

            if (mediaProjection == null) {

                Log.e(
                    TAG,
                    "MediaProjection creation failed"
                )

                cleanup()
                stopSelf()
                return
            }

            virtualDisplay =
                mediaProjection!!.createVirtualDisplay(
                    "StellarVideoR",
                    width,
                    height,
                    resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    encoderInputSurface,
                    null,
                    null
                )

            stopRequested = false
            isRecording = true

            recordingThread =
                Thread {

                    drainEncoder()

                }.apply {

                    name =
                        "StellarVideoR-Encoder"

                    start()
                }

            Log.d(
                TAG,
                "Recording started: ${outputFile.absolutePath}"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Recording start failed",
                e
            )

            cleanup()
            stopSelf()
        }
    }

    private fun drainEncoder() {

        val codec = encoder ?: return

        val bufferInfo =
            MediaCodec.BufferInfo()

        var endOfStream = false

        while (!endOfStream) {

            val outputIndex =
                try {

                    codec.dequeueOutputBuffer(
                        bufferInfo,
                        10_000
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "dequeueOutputBuffer failed",
                        e
                    )

                    break
                }

            when {

                outputIndex ==
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                    if (muxerStarted) {
                        continue
                    }

                    val newFormat =
                        codec.outputFormat

                    videoTrack =
                        muxer!!.addTrack(
                            newFormat
                        )

                    muxer!!.start()

                    muxerStarted = true

                    Log.d(
                        TAG,
                        "Muxer started"
                    )
                }

                outputIndex >= 0 -> {

                    val outputBuffer =
                        codec.getOutputBuffer(
                            outputIndex
                        )

                    if (
                        outputBuffer != null &&
                        bufferInfo.size > 0 &&
                        muxerStarted
                    ) {

                        outputBuffer.position(
                            bufferInfo.offset
                        )

                        outputBuffer.limit(
                            bufferInfo.offset +
                                    bufferInfo.size
                        )

                        muxer!!.writeSampleData(
                            videoTrack,
                            outputBuffer,
                            bufferInfo
                        )
                    }

                    codec.releaseOutputBuffer(
                        outputIndex,
                        false
                    )

                    if (
                        bufferInfo.flags and
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM !=
                        0
                    ) {
                        endOfStream = true
                    }
                }
            }

            if (
                stopRequested &&
                !endOfStream
            ) {

                try {

                    codec.signalEndOfInputStream()

                } catch (e: Exception) {

                    Log.w(
                        TAG,
                        "signalEndOfInputStream failed",
                        e
                    )
                }

                stopRequested = false
            }
        }
    }

    private fun stopRecording() {

        if (!isRecording) {
            stopSelf()
            return
        }

        Log.d(
            TAG,
            "Stopping recording"
        )

        stopRequested = true

        try {

            encoder?.signalEndOfInputStream()

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Could not signal EOS",
                e
            )
        }

        try {

            recordingThread?.join(3000)

        } catch (e: InterruptedException) {

            Thread.currentThread().interrupt()
        }

        cleanup()

        stopSelf()
    }

    private fun cleanup() {

        isRecording = false
        stopRequested = false

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }

        virtualDisplay = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {
        }

        mediaProjection = null

        try {
            encoderInputSurface?.release()
        } catch (_: Exception) {
        }

        encoderInputSurface = null

        try {
            encoder?.stop()
        } catch (_: Exception) {
        }

        try {
            encoder?.release()
        } catch (_: Exception) {
        }

        encoder = null

        try {

            if (muxerStarted) {
                muxer?.stop()
            }

        } catch (_: Exception) {
        }

        try {
            muxer?.release()
        } catch (_: Exception) {
        }

        muxer = null
        muxerStarted = false
        videoTrack = -1

        recordingThread = null

        Log.d(
            TAG,
            "Recording resources released"
        )
    }

    private fun findEncoder(
        width: Int,
        height: Int,
        fps: Int
    ): MediaCodecInfo? {

        val codecList =
            MediaCodecList(
                MediaCodecList.REGULAR_CODECS
            )

        for (info in codecList.codecInfos) {

            if (!info.isEncoder) {
                continue
            }

            if (
                !info.supportedTypes.any {
                    it.equals(
                        MIME_TYPE,
                        ignoreCase = true
                    )
                }
            ) {
                continue
            }

            try {

                val capabilities =
                    info.getCapabilitiesForType(
                        MIME_TYPE
                    )

                val videoCapabilities =
                    capabilities.videoCapabilities
                        ?: continue

                if (
                    !videoCapabilities.isSizeSupported(
                        width,
                        height
                    )
                ) {
                    Log.d(
                        TAG,
                        "${info.name}: size unsupported"
                    )

                    continue
                }

                val frameRates =
                    videoCapabilities
                        .getSupportedFrameRatesFor(
                            width,
                            height
                        )

                if (
                    frameRates.upper <
                    fps.toDouble()
                ) {

                    Log.d(
                        TAG,
                        "${info.name}: FPS unsupported"
                    )

                    continue
                }

                return info

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Capability check failed for ${info.name}",
                    e
                )
            }
        }

        return null
    }

    private fun calculateBitrate(
        width: Int,
        height: Int,
        fps: Int
    ): Int {

        return when {

            width >= 3000 &&
                    height >= 1400 &&
                    fps >= 120 -> 50_000_000

            width >= 3000 &&
                    height >= 1400 -> 35_000_000

            width >= 2000 &&
                    fps >= 120 -> 35_000_000

            width >= 2000 -> 25_000_000

            fps >= 60 -> 16_000_000

            else -> 10_000_000
        }
    }

    private fun createOutputFile(): File {

        val movies =
            getExternalFilesDir(
                android.os.Environment.DIRECTORY_MOVIES
            )

        val directory =
            File(
                movies,
                "Stellar VideoR"
            )

        if (!directory.exists()) {
            directory.mkdirs()
        }

        val timestamp =
            System.currentTimeMillis()

        return File(
            directory,
            "StellarVideoR_$timestamp.mp4"
        )
    }

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Ekran Kaydı",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "Stellar VideoR ekran kayıt bildirimi"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun createNotification(): Notification {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Notification.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle(
                    "Ekran kaydediliyor"
                )
                .setContentText(
                    "Stellar VideoR aktif"
                )
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setOngoing(true)
                .build()

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(
                    "Ekran kaydediliyor"
                )
                .setContentText(
                    "Stellar VideoR aktif"
                )
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {

        if (isRecording) {
            cleanup()
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
