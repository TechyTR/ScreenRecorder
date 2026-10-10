package com.nevruz.videor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

/**
 * A transparent trampoline for Android's required MediaProjection consent UI.
 * It lets the Quick Settings tile request consent without opening the app's
 * full-screen settings panel over the game.
 */
class ProjectionPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_MEDIA_PROJECTION) return

        if (resultCode == RESULT_OK && data != null) {
            val settings = RecordingPreferences.load(this)
            RecordingController.start(
                context = this,
                resultCode = resultCode,
                data = data,
                width = settings.width,
                height = settings.height,
                fps = settings.fps
            )
        } else {
            Toast.makeText(this, "Ekran kaydı izni verilmedi.", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1101
    }
}
