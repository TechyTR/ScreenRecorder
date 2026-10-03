package com.nevruz.videor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
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
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import java.io.File

class ScreenRecordService : Service() {

    companion object {

        private const val TAG = "StellarVideoR"

        const val ACTION_START =
            "com.nevruz.videor.action.START"

        const val ACTION_STOP =
            "com.nevruz.videor.action.STOP"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_FPS = "fps"

        const val ACTION_STATE_CHANGED =
            "com.nevruz.videor.STATE_CHANGED"

        const val EXTRA_RECORDING =
            "recording"

        private const val CHANNEL_ID =
            "stellar_videor_recording"

        private const val NOTIFICATION_ID = 1001

        private const val MIME_TYPE =
            "video/avc"

        @Volatile
        var isCurrentlyRecording = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null

    private var inputSurface: android.view.Surface? = null
    private var recordingThread: Thread? = null

    private var outputUri: Uri? = null
    private var outputStreamFile: File? = null

    private var muxerStarted = false
    private var videoTrack = -1

    @Volatile
    private var stopRequested = false

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

                if (!isCurrentlyRecording) {

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
                createNotification(
                    width,
                    height,
                    fps
                ),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
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
                    "No compatible AVC encoder"
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

            inputSurface =
                codec.createInputSurface()

            codec.start()

            val output =
                createMediaStoreOutput()

            outputUri = output.first
            outputStreamFile = output.second

            muxer =
                MediaMuxer(
                    outputStreamFile!!.absolutePath,
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
                throw IllegalStateException(
                    "MediaProjection unavailable"
                )
            }

            virtualDisplay =
                mediaProjection!!.createVirtualDisplay(
                    "StellarVideoR",
                    width,
                    height,
                    resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    inputSurface,
                    null,
                    null
                )

            stopRequested = false
            isCurrentlyRecording = true

            sendState(true)

            recordingThread =
                Thread {
                    drainEncoder()
                }.apply {
                    name = "StellarVideoR-Encoder"
                    start()
                }

            Log.d(
                TAG,
                "Recording started"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Recording failed to start",
                e
            )

            cleanup(false)
            stopSelf()
        }
    }

    private fun drainEncoder() {

        val codec = encoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        var eos = false

        while (!eos) {

            val index =
                try {
                    codec.dequeueOutputBuffer(
                        bufferInfo,
                        10_000
                    )
                } catch (e: Exception) {
                    Log.e(
                        TAG,
                        "Encoder drain error",
                        e
                    )
                    break
                }

            when {

                index ==
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                    if (!muxerStarted) {

                        val format =
                            codec.outputFormat

                        videoTrack =
                            muxer!!.addTrack(
                                format
                            )

                        muxer!!.start()
                        muxerStarted = true
                    }
                }

                index >= 0 -> {

                    val buffer =
                        codec.getOutputBuffer(index)

                    if (
                        buffer != null &&
                        bufferInfo.size > 0 &&
                        muxerStarted
                    ) {

                        buffer.position(
                            bufferInfo.offset
                        )

                        buffer.limit(
                            bufferInfo.offset +
                                    bufferInfo.size
                        )

                        muxer!!.writeSampleData(
                            videoTrack,
                            buffer,
                            bufferInfo
                        )
                    }

                    codec.releaseOutputBuffer(
                        index,
                        false
                    )

                    if (
                        bufferInfo.flags and
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM !=
                        0
                    ) {
                        eos = true
                    }
                }
            }
        }
    }

    private fun stopRecording() {

        if (!isCurrentlyRecording) {
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
                "EOS signal failed",
                e
            )
        }

        try {
            recordingThread?.join(4000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        cleanup(true)

        stopSelf()
    }

    private fun cleanup(
        successful: Boolean
    ) {

        isCurrentlyRecording = false

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
            inputSurface?.release()
        } catch (_: Exception) {
        }

        inputSurface = null

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

        if (successful) {
            finalizeMediaStoreFile()
        } else {
            deleteFailedMedia()
        }

        sendState(false)

        outputUri = null
        outputStreamFile = null
    }

    private fun createMediaStoreOutput(): Pair<Uri, File> {

        val resolver = contentResolver

        val name =
            "StellarVideoR_${
                System.currentTimeMillis()
            }.mp4"

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    name
                )

                put(
                    MediaStore.Video.Media.MIME_TYPE,
                    "video/mp4"
                )

                put(
                    MediaStore.Video.Media.TITLE,
                    "Stellar VideoR"
                )

                if (Build.VERSION.SDK_INT >= 29) {

                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_DCIM +
                                "/Screen Recordings"
                    )

                    put(
                        MediaStore.Video.Media.IS_PENDING,
                        1
                    )
                }
            }

        val uri =
            resolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            )
                ?: throw IllegalStateException(
                    "MediaStore insert failed"
                )

        val temporaryFile =
            File(
                cacheDir,
                "recording_${System.currentTimeMillis()}.mp4"
            )

        return uri to temporaryFile
    }

    private fun finalizeMediaStoreFile() {

        val uri = outputUri ?: return
        val temp = outputStreamFile ?: return

        try {

            contentResolver.openOutputStream(
                uri
            )?.use { output ->

                temp.inputStream().use { input ->

                    input.copyTo(output)
                }
            }

            if (Build.VERSION.SDK_INT >= 29) {

                val values =
                    ContentValues().apply {

                        put(
                            MediaStore.Video.Media.IS_PENDING,
                            0
                        )
                    }

                contentResolver.update(
                    uri,
                    values,
                    null,
                    null
                )
            }

            temp.delete()

            Log.d(
                TAG,
                "Saved to DCIM/Screen Recordings"
            )

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Could not finalize video",
                e
            )

            try {
                contentResolver.delete(
                    uri,
                    null,
                    null
                )
            } catch (_: Exception) {
            }

            temp.delete()
        }
    }

    private fun deleteFailedMedia() {

        outputStreamFile?.delete()

        outputUri?.let {

            try {
                contentResolver.delete(
                    it,
                    null,
                    null
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun findEncoder(
        width: Int,
        height: Int,
        fps: Int
    ): MediaCodecInfo? {

        val list =
            MediaCodecList(
                MediaCodecList.REGULAR_CODECS
            )

        for (info in list.codecInfos) {

            if (!info.isEncoder) continue

            if (
                !info.supportedTypes.any {
                    it.equals(
                        MIME_TYPE,
                        true
                    )
                }
            ) continue

            try {

                val caps =
                    info.getCapabilitiesForType(
                        MIME_TYPE
                    )

                val video =
                    caps.videoCapabilities
                        ?: continue

                if (
                    !video.isSizeSupported(
                        width,
                        height
                    )
                ) continue

                val rates =
                    video.getSupportedFrameRatesFor(
                        width,
                        height
                    )

                if (
                    rates.upper <
                    fps.toDouble()
                ) continue

                return info

            } catch (_: Exception) {
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
                    fps >= 120 ->
                50_000_000

            width >= 3000 &&
                    height >= 1400 ->
                35_000_000

            width >= 2000 &&
                    fps >= 120 ->
                35_000_000

            width >= 2000 ->
                25_000_000

            fps >= 60 ->
                16_000_000

            else ->
                10_000_000
        }
    }

    private fun sendState(
        recording: Boolean
    ) {

        sendBroadcast(
            Intent(ACTION_STATE_CHANGED).apply {

                setPackage(packageName)

                putExtra(
                    EXTRA_RECORDING,
                    recording
                )
            }
        )
    }

    private fun createNotificationChannel() {

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

    private fun createNotification(
        width: Int,
        height: Int,
        fps: Int
    ): Notification {

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                R.drawable.ic_videor
            )
            .setContentTitle(
                "Ekran kaydediliyor"
            )
            .setContentText(
                "${width}×${height} • ${fps} FPS"
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    override fun onDestroy() {

        if (isCurrentlyRecording) {
            cleanup(false)
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
