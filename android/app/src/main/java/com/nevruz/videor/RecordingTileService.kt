package com.nevruz.videor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object RecordingNotification {

    const val CHANNEL_ID =
        "stellar_videor_recording"

    const val NOTIFICATION_ID =
        1001

    fun createChannel(context: Context) {

        if (Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "Ekran Kaydı",
                NotificationManager.IMPORTANCE_LOW
            ).apply {

                description =
                    "Stellar VideoR ekran kayıt durumu"

                setShowBadge(false)
            }

        val manager =
            context.getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(channel)
    }

    fun create(
        context: Context,
        elapsed: String,
        width: Int,
        height: Int,
        fps: Int
    ): Notification {

        val openIntent =
            Intent(
                context,
                MainActivity::class.java
            ).apply {

                flags =
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                2001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                        PendingIntent.FLAG_IMMUTABLE
            )

        val builder =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                Notification.Builder(
                    context,
                    CHANNEL_ID
                )

            } else {

                @Suppress("DEPRECATION")
                Notification.Builder(context)
            }

        return builder
            .setSmallIcon(
                R.drawable.ic_videor
            )
            .setContentTitle(
                "Ekran kaydediliyor"
            )
            .setContentText(
                "$elapsed • ${width}×${height} • ${fps} FPS"
            )
            .setSubText(
                "Stellar VideoR"
            )
            .setContentIntent(
                pendingIntent
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }
}
