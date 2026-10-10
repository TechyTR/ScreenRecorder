package com.nevruz.videor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast

class ScreenRecordService : Service() {

    companion object {
        const val ACTION_START = "com.nevruz.videor.START"
        const val ACTION_STOP = "com.nevruz.videor.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"
        const val EXTRA_FPS = "fps"
        const val ACTION_STATE_CHANGED = "com.nevruz.videor.action.STATE_CHANGED"

        @Volatile
        var isCurrentlyRecording: Boolean = false
            private set

        private const val CHANNEL_ID = "screen_recording"
        private const val NOTIFICATION_ID = 5001
        private const val TAG = "ScreenRecordService"
    }

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var output: RecordingOutput.Output? = null
    private var recording = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startFromIntent(intent)
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startFromIntent(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
        }
        if (resultCode != -1 && data != null) {
            startRecording(
                resultCode,
                data,
                intent.getIntExtra(EXTRA_WIDTH, resources.displayMetrics.widthPixels),
                intent.getIntExtra(EXTRA_HEIGHT, resources.displayMetrics.heightPixels),
                intent.getIntExtra(EXTRA_FPS, 60)
            )
        }
    }

    private fun startRecording(resultCode: Int, data: Intent, width: Int, height: Int, fps: Int) {
        if (recording) return

        try {
            val audioMode = RecordingPreferences.load(this).audioMode
            val microphoneEnabled = audioMode == AudioMode.MICROPHONE ||
                audioMode == AudioMode.MICROPHONE_AND_MEDIA
            startForegroundCompat(microphoneEnabled)

            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
                ?: error("MediaProjection oluşturulamadı.")
            projectionCallback = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (recording) stopRecording()
                }
            }
            projection!!.registerCallback(projectionCallback!!, android.os.Handler(mainLooper))

            val session = createRecorderWithFallback(width, height, fps, microphoneEnabled)
            recorder = session.recorder
            output = session.output
            virtualDisplay = projection!!.createVirtualDisplay(
                "Stellar VideoR",
                session.width,
                session.height,
                resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                session.recorder.surface,
                null,
                null
            )

            recording = true
            isCurrentlyRecording = true
            broadcastStateChanged()

            if (audioMode == AudioMode.MEDIA || audioMode == AudioMode.MICROPHONE_AND_MEDIA) {
                Toast.makeText(
                    this,
                    "Medya sesi bu kayıt modunda kullanılamıyor; görüntü${if (microphoneEnabled) " ve mikrofon" else ""} kaydediliyor.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (error: Throwable) {
            Log.e(TAG, "Could not start screen recording", error)
            Toast.makeText(
                applicationContext,
                "Kayıt başlatılamadı: ${error.localizedMessage ?: error.javaClass.simpleName}",
                Toast.LENGTH_LONG
            ).show()
            cleanup(saveRecording = false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createRecorderWithFallback(
        requestedWidth: Int,
        requestedHeight: Int,
        requestedFps: Int,
        microphoneEnabled: Boolean
    ): RecorderSession {
        val shortSide = minOf(requestedWidth, requestedHeight)
        val longSide = maxOf(requestedWidth, requestedHeight)
        val fallbackLong = (longSide.toFloat() * 720f / shortSide).toInt() and 1.inv()
        val fallbackSize = if (requestedWidth <= requestedHeight) 720 to fallbackLong else fallbackLong to 720
        val candidates = linkedSetOf(
            Triple(requestedWidth, requestedHeight, requestedFps),
            Triple(requestedWidth, requestedHeight, 60),
            Triple(requestedWidth, requestedHeight, 30),
            Triple(fallbackSize.first, fallbackSize.second, 30)
        )

        var lastError: Throwable? = null
        for ((width, height, fps) in candidates) {
            var candidateOutput: RecordingOutput.Output? = null
            var candidateRecorder: MediaRecorder? = null
            try {
                val createdOutput = RecordingOutput.create(this)
                candidateOutput = createdOutput
                val createdRecorder = MediaRecorder().apply {
                    if (microphoneEnabled) setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(createdOutput.fileDescriptor)
                    setVideoSize(width, height)
                    setVideoFrameRate(fps)
                    setVideoEncodingBitRate(calculateBitrate(width, height, fps))
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    if (microphoneEnabled) {
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(128_000)
                        setAudioSamplingRate(48_000)
                    }
                    prepare()
                    start()
                }
                candidateRecorder = createdRecorder
                return RecorderSession(createdRecorder, createdOutput, width, height)
            } catch (error: Throwable) {
                Log.w(TAG, "Recorder profile $width×$height @ $fps FPS failed", error)
                lastError = error
                runCatching { candidateRecorder?.reset() }
                runCatching { candidateRecorder?.release() }
                candidateOutput?.let { RecordingOutput.delete(this, it) }
            }
        }
        throw lastError ?: IllegalStateException("Video recorder could not be created.")
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        isCurrentlyRecording = false
        broadcastStateChanged()
        cleanup(saveRecording = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup(saveRecording: Boolean) {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null

        var saved = saveRecording
        runCatching { recorder?.stop() }.onFailure {
            Log.w(TAG, "Recorder did not produce a valid video", it)
            saved = false
        }
        runCatching { recorder?.reset() }
        runCatching { recorder?.release() }
        recorder = null

        runCatching { projectionCallback?.let { projection?.unregisterCallback(it) } }
        projectionCallback = null
        runCatching { projection?.stop() }
        projection = null

        output?.let {
            if (saved) RecordingOutput.finish(this, it) else RecordingOutput.delete(this, it)
        }
        output = null
    }

    private fun calculateBitrate(width: Int, height: Int, fps: Int): Int {
        return (width.toLong() * height * fps * 0.14)
            .coerceIn(4_000_000L, 60_000_000L)
            .toInt()
    }

    private fun startForegroundCompat(microphoneEnabled: Boolean) {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Ekran Kaydedici")
            .setContentText("Ekran kaydediliyor")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && microphoneEnabled) {
                serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ekran Kaydı", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun broadcastStateChanged() {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    override fun onDestroy() {
        recording = false
        isCurrentlyRecording = false
        cleanup(saveRecording = false)
        broadcastStateChanged()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class RecorderSession(
        val recorder: MediaRecorder,
        val output: RecordingOutput.Output,
        val width: Int,
        val height: Int
    )
}
