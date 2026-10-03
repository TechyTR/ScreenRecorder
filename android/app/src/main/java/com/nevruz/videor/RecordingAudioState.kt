package com.nevruz.videor

data class RecordingAudioState(
    val mode: AudioMode = AudioMode.OFF,
    val active: Boolean = false,
    val microphoneActive: Boolean = false,
    val mediaActive: Boolean = false
)
