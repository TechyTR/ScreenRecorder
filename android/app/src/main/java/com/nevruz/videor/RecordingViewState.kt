package com.nevruz.videor

data class RecordingViewState(

    val state: RecordingState =
        RecordingState.IDLE,

    val elapsed: String =
        "00:00:00",

    val width: Int =
        2340,

    val height: Int =
        1080,

    val fps: Int =
        60
)
