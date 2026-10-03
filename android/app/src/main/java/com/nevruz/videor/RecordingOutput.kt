package com.nevruz.videor

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object RecordingOutput {

    fun create(
        context: Context
    ): Output {

        val timestamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(Date())

        val fileName =
            "Screen_Record_$timestamp.mp4"

        val values =
            ContentValues().apply {

                put(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    fileName
                )

                put(
                    MediaStore.Video.Media.MIME_TYPE,
                    "video/mp4"
                )

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

        val uri =
            context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            )
                ?: error(
                    "Kayıt dosyası oluşturulamadı."
                )

        val descriptor =
            context.contentResolver.openFileDescriptor(
                uri,
                "rw"
            )
                ?: error(
                    "Dosya açılamadı."
                )

        return Output(
            uri = uri,
            fileDescriptor =
                descriptor.fileDescriptor,
            descriptor = descriptor
        )
    }

    fun finish(
        context: Context,
        output: Output
    ) {

        output.descriptor.close()

        val values =
            ContentValues().apply {
                put(
                    MediaStore.Video.Media.IS_PENDING,
                    0
                )
            }

        context.contentResolver.update(
            output.uri,
            values,
            null,
            null
        )
    }

    fun delete(
        context: Context,
        output: Output
    ) {

        runCatching {
            output.descriptor.close()
        }

        runCatching {
            context.contentResolver.delete(
                output.uri,
                null,
                null
            )
        }
    }

    data class Output(
        val uri: android.net.Uri,
        val fileDescriptor: java.io.FileDescriptor,
        val descriptor:
            android.os.ParcelFileDescriptor
    )
}
