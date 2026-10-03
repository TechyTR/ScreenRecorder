package com.nevruz.videor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.Environment
import java.io.File

class ScreenRecordService : Service() {

    companion object {
        const val ACTION_START =
            "com.stellar.videor.START_RECORDING"

        const val ACTION_STOP =
            "com.stellar.videor.STOP_RECORDING"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        private const val CHANNEL_ID = "screen_recording"
        private const val NOTIFICATION_ID = 5001
    }

    private var mediaRecorder: MediaRecorder? = null
    private var mediaProjection: MediaProjection? = null

    private var isRecording = false

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
                            intent.getParcelableExtra(EXTRA_DATA)
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

        val notification = createNotification(
            "Ekran kaydediliyor"
        )

        if (Build.VERSION.SDK_INT >= 29) {
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

        val projectionManager =
            getSystemService(
                Context.MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        mediaProjection =
            projectionManager.getMediaProjection(
                resultCode,
                data
            )

        val outputFile = createOutputFile()

        mediaRecorder = MediaRecorder(this).apply {

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
                12_000_000
            )

            setVideoFrameRate(60)

            setVideoSize(
                1080,
                1920
            )

            setOutputFile(
                outputFile.absolutePath
            )

            prepare()
        }

        val surface =
            mediaRecorder!!.surface

        mediaProjection!!.createVirtualDisplay(
            "StellarVideoR",
            1080,
            1920,
            resources.displayMetrics.densityDpi,
            0,
            surface,
            null,
            null
        )

        mediaRecorder!!.start()

        isRecording = true
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

    private fun stopRecording() {

        if (!isRecording) {
            stopSelf()
            return
        }

        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }

        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null

        mediaProjection?.stop()
        mediaProjection = null

        isRecording = false

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ekran Kaydı",
            NotificationManager.IMPORTANCE_LOW
        )

        manager.createNotificationChannel(channel)
    }

    private fun createNotification(
        text: String
    ): Notification {

        return Notification.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("Ekran Kaydedici")
            .setContentText(text)
            .setSmallIcon(
                android.R.drawable.ic_menu_camera
            )
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
