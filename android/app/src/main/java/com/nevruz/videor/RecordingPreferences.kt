package com.nevruz.videor

import android.content.Context

object RecordingPreferences {

    private const val PREFS = "recording_preferences"

    private const val AUDIO = "audio"
    private const val SCREEN = "screen"
    private const val WIDTH = "width"
    private const val HEIGHT = "height"
    private const val FPS = "fps"

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

    fun load(context: Context): RecordingSettings {

        val p = prefs(context)

        val audio = runCatching {
            AudioMode.valueOf(
                p.getString(
                    AUDIO,
                    AudioMode.OFF.name
                )!!
            )
        }.getOrDefault(AudioMode.OFF)

        val screen = runCatching {
            ScreenMode.valueOf(
                p.getString(
                    SCREEN,
                    ScreenMode.FULL_SCREEN.name
                )!!
            )
        }.getOrDefault(ScreenMode.FULL_SCREEN)

        return RecordingSettings(
            audioMode = audio,
            screenMode = screen,
            width = p.getInt(WIDTH, 2340),
            height = p.getInt(HEIGHT, 1080),
            fps = p.getInt(FPS, 60)
        )
    }

    fun save(
        context: Context,
        settings: RecordingSettings
    ) {

        prefs(context)
            .edit()
            .putString(
                AUDIO,
                settings.audioMode.name
            )
            .putString(
                SCREEN,
                settings.screenMode.name
            )
            .putInt(
                WIDTH,
                settings.width
            )
            .putInt(
                HEIGHT,
                settings.height
            )
            .putInt(
                FPS,
                settings.fps
            )
            .apply()
    }
}
