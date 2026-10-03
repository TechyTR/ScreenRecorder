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
import android.os.Environment
import android.os.IBinder
import android.util.Log
import java.io.File

class ScreenRecordService : Service() {

    companion object {

        const val ACTION_START =
            "com.nevruz.videor.START_RECORDING"

        const val ACTION_STOP =
            "com.nevruz.videor.STOP_RECORDING"

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

        private const val TAG =
            "StellarVideoR"

        private const val CHANNEL_ID =
            "stellar_video_recording"

        private const val NOTIFICATION_ID =
            5001

        private const val MIME_TYPE =
            MediaFormat.MIMETYPE_VIDEO_AVC

        private const val DEFAULT_WIDTH =
            1440

        private const val DEFAULT_HEIGHT =
            3120

        private const val DEFAULT_FPS =
            120

        private const val DEFAULT_BITRATE =
            50_000_000
    }

    private var mediaProjection: MediaProjection? = null

    private var virtualDisplay: VirtualDisplay? = null

    private var encoder: MediaCodec? = null

    private var muxer: MediaMuxer? = null

    private var inputSurface: android.view.Surface? = null

    private var encoderThread: Thread? = null

    private var muxerStarted = false

    private var videoTrack = -1

    private var isRecording = false

    private var recordingWidth = DEFAULT_WIDTH
    private var recordingHeight = DEFAULT_HEIGHT
    private var recordingFps = DEFAULT_FPS

    private var outputFile: File? = null

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

                    val projectionData =
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

                    if (
                        projectionData != null &&
                        resultCode != -1
                    ) {

                        recordingWidth =
                            intent.getIntExtra(
                                EXTRA_WIDTH,
                                DEFAULT_WIDTH
                            )

                        recordingHeight =
                            intent.getIntExtra(
                                EXTRA_HEIGHT,
                                DEFAULT_HEIGHT
                            )

                        recordingFps =
                            intent.getIntExtra(
                                EXTRA_FPS,
                                DEFAULT_FPS
                            )

                        startRecording(
                            resultCode,
                            projectionData
                        )
                    }
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
        projectionData: Intent
    ) {

        try {

            startForegroundCompat()

            Log.d(
                TAG,
                "Starting MediaCodec recording"
            )

            Log.d(
                TAG,
                "Resolution: ${recordingWidth}x$recordingHeight"
            )

            Log.d(
                TAG,
                "FPS: $recordingFps"
            )

            val codecName =
                findEncoder()

            if (codecName == null) {

                Log.e(
                    TAG,
                    "No AVC hardware encoder found"
                )

                stopSelf()
                return
            }

            Log.d(
                TAG,
                "Encoder: $codecName"
            )

            if (
                !isConfigurationSupported(
                    codecName,
                    recordingWidth,
                    recordingHeight,
                    recordingFps
                )
            ) {

                Log.e(
                    TAG,
                    "Requested configuration is unsupported"
                )

                stopSelf()
                return
            }

            val format =
                MediaFormat.createVideoFormat(
                    MIME_TYPE,
                    recordingWidth,
                    recordingHeight
                )

            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities
                    .COLOR_FormatSurface
            )

            format.setInteger(
                MediaFormat.KEY_BIT_RATE,
                calculateBitrate()
            )

            format.setInteger(
                MediaFormat.KEY_FRAME_RATE,
                recordingFps
            )

            format.setInteger(
                MediaFormat.KEY_I_FRAME_INTERVAL,
                1
            )

            /*
             * Constant bitrate.
             */
            if (
                format.containsKey(
                    MediaFormat.KEY_BITRATE_MODE
                )
            ) {

                format.setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities
                        .BITRATE_MODE_CBR
                )
            }

            encoder =
                MediaCodec.createByCodecName(
                    codecName
                )

            encoder!!.configure(
                format,
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )

            inputSurface =
                encoder!!.createInputSurface()

            encoder!!.start()

            outputFile =
                createOutputFile()

            muxer =
                MediaMuxer(
                    outputFile!!.absolutePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )

            val projectionManager =
                getSystemService(
                    Context.MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            mediaProjection =
                projectionManager.getMediaProjection(
                    resultCode,
                    projectionData
                )

            virtualDisplay =
                mediaProjection!!.createVirtualDisplay(
                    "Stellar VideoR",
                    recordingWidth,
                    recordingHeight,
                    resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface,
                    null,
                    null
                )

            isRecording = true

            encoderThread =
                Thread {

                    drainEncoder()

                }.apply {

                    name =
                        "StellarVideoR-Encoder"

                    start()
                }

            Log.d(
                TAG,
                "Recording started successfully"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Recording start failed",
                e
            )

            stopRecording()
        }
    }

    private fun drainEncoder() {

        val bufferInfo =
            MediaCodec.BufferInfo()

        try {

            while (isRecording) {

                val codec =
                    encoder ?: break

                val outputIndex =
                    codec.dequeueOutputBuffer(
                        bufferInfo,
                        10_000
                    )

                when {

                    outputIndex ==
                            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                        if (muxerStarted) {
                            continue
                        }

                        val newFormat =
                            codec.outputFormat

                        Log.d(
                            TAG,
                            "Encoder output format: $newFormat"
                        )

                        videoTrack =
                            muxer!!.addTrack(
                                newFormat
                            )

                        muxer!!.start()

                        muxerStarted = true
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
                    }
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Encoder drain failed",
                e
            )
        }

        /*
         * Son buffer'ları almaya çalış.
         */
        try {

            val codec =
                encoder ?: return

            val endInfo =
                MediaCodec.BufferInfo()

            var attempts = 0

            while (attempts < 50) {

                val index =
                    codec.dequeueOutputBuffer(
                        endInfo,
                        10_000
                    )

                if (index >= 0) {

                    val buffer =
                        codec.getOutputBuffer(
                            index
                        )

                    if (
                        buffer != null &&
                        endInfo.size > 0 &&
                        muxerStarted
                    ) {

                        buffer.position(
                            endInfo.offset
                        )

                        buffer.limit(
                            endInfo.offset +
                                    endInfo.size
                        )

                        muxer!!.writeSampleData(
                            videoTrack,
                            buffer,
                            endInfo
                        )
                    }

                    codec.releaseOutputBuffer(
                        index,
                        false
                    )

                } else {

                    attempts++
                }
            }

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Final drain failed",
                e
            )
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

        isRecording = false

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
            encoderThread?.join(1500)
        } catch (_: Exception) {
        }

        encoderThread = null

        try {
            encoder?.signalEndOfInputStream()
        } catch (_: Exception) {
        }

        try {
            encoder?.stop()
        } catch (_: Exception) {
        }

        try {
            encoder?.release()
        } catch (_: Exception) {
        }

        encoder = null

        inputSurface = null

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

        Log.d(
            TAG,
            "Recording stopped: ${outputFile?.absolutePath}"
        )

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    private fun findEncoder(): String? {

        val codecList =
            MediaCodecList(
                MediaCodecList.REGULAR_CODECS
            )

        for (
            codecInfo in codecList.codecInfos
        ) {

            if (!codecInfo.isEncoder) {
                continue
            }

            if (
                codecInfo.supportedTypes.any {
                    it.equals(
                        MIME_TYPE,
                        ignoreCase = true
                    )
                }
            ) {

                Log.d(
                    TAG,
                    "Found AVC encoder: ${codecInfo.name}"
                )

                return codecInfo.name
            }
        }

        return null
    }

    private fun isConfigurationSupported(
        codecName: String,
        width: Int,
        height: Int,
        fps: Int
    ): Boolean {

        return try {

            val codecInfo =
                MediaCodecList(
                    MediaCodecList.REGULAR_CODECS
                )
                    .codecInfos
                    .firstOrNull {
                        it.name == codecName
                    }
                    ?: return false

            val capabilities =
                codecInfo.getCapabilitiesForType(
                    MIME_TYPE
                )

            val video =
                capabilities.videoCapabilities

            if (
                !video.isSizeSupported(
                    width,
                    height
                )
            ) {

                Log.e(
                    TAG,
                    "Resolution unsupported: ${width}x$height"
                )

                return false
            }

            val frameRateRange =
                video.getSupportedFrameRatesFor(
                    width,
                    height
                )

            Log.d(
                TAG,
                "Supported FPS range: $frameRateRange"
            )

            if (
                frameRateRange.upper < fps
            ) {

                Log.e(
                    TAG,
                    "FPS unsupported: requested=$fps max=${frameRateRange.upper}"
                )

                return false
            }

            true

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Codec capability check failed",
                e
            )

            false
        }
    }

    private fun calculateBitrate(): Int {

        return when {

            recordingWidth >= 3000 &&
                    recordingFps >= 120 -> {
                50_000_000
            }

            recordingWidth >= 3000 -> {
                35_000_000
            }

            recordingWidth >= 2000 &&
                    recordingFps >= 120 -> {
                35_000_000
            }

            recordingWidth >= 2000 -> {
                25_000_000
            }

            recordingFps >= 120 -> {
                25_000_000
            }

            else -> {
                12_000_000
            }
        }
    }

    private fun createOutputFile(): File {

        val moviesDirectory =
            getExternalFilesDir(
                Environment.DIRECTORY_MOVIES
            )

        val directory =
            File(
                moviesDirectory,
                "Stellar VideoR"
            )

        if (!directory.exists()) {
            directory.mkdirs()
        }

        return File(
            directory,
            "Recording_${System.currentTimeMillis()}.mp4"
        )
    }

    private fun startForegroundCompat() {

        val notification =
            createNotification()

        if (Build.VERSION.SDK_INT >= 29) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun createNotification(): Notification {

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle(
                "Ekran Kaydedici"
            )
            .setContentText(
