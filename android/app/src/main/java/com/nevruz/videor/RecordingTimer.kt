package com.nevruz.videor

class RecordingTimer {

    private var startedAt = 0L

    fun start() {
        startedAt =
            System.currentTimeMillis()
    }

    fun reset() {
        startedAt = 0L
    }

    fun elapsed(): Long {

        if (startedAt == 0L) {
            return 0L
        }

        return System.currentTimeMillis() -
                startedAt
    }

    fun formatted(): String {

        val total =
            elapsed() / 1000L

        val hours =
            total / 3600

        val minutes =
            (total % 3600) / 60

        val seconds =
            total % 60

        return String.format(
            "%02d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    }
}
