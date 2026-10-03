package com.nevruz.videor

data class ResolutionOption(
    val title: String,
    val width: Int,
    val height: Int,
    val fps: Int
) {
    override fun toString(): String {
        return "$title • ${fps} FPS"
    }
}
