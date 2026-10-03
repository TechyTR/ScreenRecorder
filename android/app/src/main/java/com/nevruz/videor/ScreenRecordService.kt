package com.nevruz.videor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.view.WindowManager
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

        private const val CHANNEL_ID =
            "stellar_video_recording"

        private const val NOTIFICATION_ID =
            5001

        private const val FPS = 120

        private const val BITRATE = 40_000_000
    }

    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null

    private var recording = false

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

                if (!recording) {

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

                    if (data != null) {

                        startRecording(
                            resultCode,
                            data
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
        data: Intent
    ) {

        startForegroundCompat()

        val projectionManager =
            getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        projection =
            projectionManager.getMediaProjection(
                resultCode,
                data
            )

        val size = getScreenSize()

        val width = size.first
        val height = size.second

        /*
         * QHD+ ekran:
         *
         * Dikey  = 1440 x 3120
         * Yatay  = 3120 x 1440
         *
         * Ekranın mevcut yönünü koruyoruz.
         */

        val outputFile =
            createOutputFile()

        recorder =
            MediaRecorder(this).apply {

                setVideoSource(
                    MediaRecorder.VideoSource.SURFACE
                )

                setOutputFormat(
                    MediaRecorder.OutputFormat.MPEG_4
                )

                setVideoEncoder(
                    MediaRecorder.VideoEncoder.H264
                )

                setVideoEncodingBitRate(
                    BITRATE
                )

                setVideoFrameRate(
                    FPS
                )

                setVideoSize(
                    width,
                    height
                )

                setOutputFile(
                    outputFile.absolutePath
                )

                prepare()
            }

        virtualDisplay =
            projection!!.createVirtualDisplay(
                "Stellar VideoR",
                width,
                height,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder!!.surface,
                null,
                null
            )

        recorder!!.start()

        recording = true
    }

    private fun getScreenSize(): Pair<Int, Int> {

        val windowManager =
            getSystemService(
                WindowManager::class.java
            )

        val metrics =
            resources.displayMetrics

        /*
         * Gerçek fiziksel çözünürlüğü kullanıyoruz.
         *
         * S25+ QHD+:
         * 3120 x 1440
         */

        val widthPixels =
            metrics.widthPixels

        val heightPixels =
            metrics.heightPixels

        return if (widthPixels >= heightPixels) {

            widthPixels to heightPixels

        } else {

            widthPixels to heightPixels
        }
    }

    private fun createOutputFile(): File {

        val movies =
            getExternalFilesDir(
                Environment.DIRECTORY_MOVIES
            )

        val directory =
            File(
                movies,
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

    private fun stopRecording() {

        if (!recording) {
            stopSelf()
            return
        }

        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        recorder?.reset()
        recorder?.release()
        recorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        projection?.stop()
        projection = null

        recording = false

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
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
                "QHD+ / 120 FPS kayıt yapılıyor"
            )
            .setSmallIcon(
                android.R.drawable.ic_menu_camera
            )
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Ekran Kaydı",
                NotificationManager.IMPORTANCE_LOW
            )

        manager.createNotificationChannel(
            channel
        )
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
