package com.nevruz.videor

data class RecordingSettings(
    val audioMode: AudioMode = AudioMode.OFF,
    val screenMode: ScreenMode = ScreenMode.FULL_SCREEN,
    val width: Int = 2340,
    val height: Int = 1080,
    val fps: Int = 60
)
