package com.nevruz.videor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Surface
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay

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

        private const val CHANNEL_ID =
            "screen_recording"

        private const val NOTIFICATION_ID =
            5001
    }

    private var projection:
            MediaProjection? = null

    private var virtualDisplay:
            VirtualDisplay? = null

    private var videoEncoder:
            VideoEncoder? = null

    private var muxer:
            VideoMuxer? = null

    private var output:
            RecordingOutput.Output? = null

    private var audioCapture:
            AudioCaptureController? = null

    private var audioEncoder:
            AacEncoder? = null

    private var audioThread:
            Thread? = null

    private var recording =
        false

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

        startForegroundCompat()

        try {

            val manager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            projection =
                manager.getMediaProjection(
                    resultCode,
                    data
                )

            if (projection == null) {
                throw IllegalStateException(
                    "MediaProjection oluşturulamadı."
                )
            }

            output =
                RecordingOutput.create(this)

            val mediaMuxer =
                MediaMuxer(
                    output!!.fileDescriptor,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )

            muxer =
                VideoMuxer(mediaMuxer)

            videoEncoder =
                VideoEncoder(
                    width,
                    height,
                    fps
                )

            videoEncoder!!.start()

            val surface =
                videoEncoder!!.inputSurface

            virtualDisplay =
                projection!!.createVirtualDisplay(
                    "Stellar VideoR",
                    width,
                    height,
                    resources.displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null
                )

            val settings =
                RecordingPreferences.load(this)

            audioMode =
                settings.audioMode

            if (
                audioMode !=
                AudioMode.OFF
            ) {

                audioCapture =
                    AudioCaptureController(
                        this,
                        projection!!,
                        audioMode
                    )

                audioEncoder =
                    AacEncoder()

                audioEncoder!!.start()

                audioCapture!!.start()

                startAudioThread()
            }

            recording = true

            startVideoDrainThread()

        } catch (e: Exception) {

            cleanup()

            stopSelf()
        }
    }

    private fun startVideoDrainThread() {

        Thread {

            val codec =
                videoEncoder?.codec()
                    ?: return@Thread

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
                            muxer?.isStarted() == true
                        ) {

                            buffer.position(
                                info.offset
                            )

                            buffer.limit(
                                info.offset +
                                        info.size
                            )

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

        }.start()
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

                val settings =
                    RecordingPreferences.load(this)

                val bufferSize =
                    2048

                val mediaBuffer =
                    ShortArray(bufferSize)

                val micBuffer =
                    ShortArray(bufferSize)

                val mixedBuffer =
                    ShortArray(bufferSize)

                var sampleCount = 0L

                var formatAdded = false

                while (recording) {

                    val mediaRead =
                        if (
                            settings.audioMode ==
                            AudioMode.MEDIA ||
                            settings.audioMode ==
                            AudioMode.MICROPHONE_AND_MEDIA
                        ) {
                            capture.readMedia(
                                mediaBuffer
                            )
                        } else {
                            0
                        }

                    val micRead =
                        if (
                            settings.audioMode ==
                            AudioMode.MICROPHONE ||
                            settings.audioMode ==
                            AudioMode.MICROPHONE_AND_MEDIA
                        ) {
                            capture.readMicrophone(
                                micBuffer
                            )
                        } else {
                            0
                        }

                    val count =
                        maxOf(
                            mediaRead,
                            micRead
                        )

                    if (count <= 0) {
                        continue
                    }

                    when (
                        settings.audioMode
                    ) {

                        AudioMode.MEDIA -> {

                            AudioMixer.copy(
                                mediaBuffer,
                                mixedBuffer
                            )
                        }

                        AudioMode.MICROPHONE -> {

                            AudioMixer.copy(
                                micBuffer,
                                mixedBuffer
                            )
                        }

                        AudioMode.MICROPHONE_AND_MEDIA -> {

                            AudioMixer.mix(
                                mediaBuffer,
                                micBuffer,
                                mixedBuffer
                            )
                        }

                        AudioMode.OFF -> {
                            continue
                        }
                    }

                    val timeUs =
                        sampleCount *
                                1_000_000L /
                                AacEncoder.SAMPLE_RATE

                    val packets =
                        encoder.encode(
                            mixedBuffer,
                            count,
                            timeUs
                        )

                    sampleCount +=
                        count /
                                AacEncoder.CHANNEL_COUNT

                    if (!formatAdded) {

                        muxer?.addAudioTrack(
                            encoder.getOutputFormat()
                        )

                        formatAdded = true
                    }

                    for (packet in packets) {
                        muxer?.writeAudio(packet)
                    }
                }
            }

        audioThread!!.start()
    }

    private fun stopRecording() {

        if (!recording) {
            return
        }

        recording = false

        runCatching {
            videoEncoder?.stop()
        }

        audioThread?.join(1500)

        runCatching {
            audioEncoder?.finish()
        }

        cleanup()

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    private fun cleanup() {

        runCatching {
            audioCapture?.stop()
        }

        runCatching {
            audioEncoder?.release()
        }

        runCatching {
            virtualDisplay?.release()
        }

        runCatching {
            projection?.stop()
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

        audioThread = null
        audioCapture = null
        audioEncoder = null
        virtualDisplay = null
        projection = null
        videoEncoder = null
        muxer = null
        output = null
    }

    private fun startForegroundCompat() {

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

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
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

    override fun onDestroy() {

        if (recording) {
            recording = false
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
