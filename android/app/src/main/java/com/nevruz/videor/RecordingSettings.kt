package com.nevruz.videor

enum class AudioMode {
    OFF,
    MEDIA,
    MICROPHONE_AND_MEDIA,
    MICROPHONE
}

enum class ScreenMode {
    FULL_SCREEN,
    CROPPED
}

data class RecordingSettings(
    val audioMode: AudioMode = AudioMode.OFF,
    val screenMode: ScreenMode = ScreenMode.FULL_SCREEN,
    val width: Int = 2340,
    val height: Int = 1080,
    val fps: Int = 60
)
