package dev.vimivo.playback

/** The playback speeds every platform offers, so the pickers and engine limits never drift apart. */
object PlaybackSpeeds {
    const val MIN = 0.25f
    const val MAX = 4f
    val RANGE: ClosedFloatingPointRange<Float> = MIN..MAX
    val PRESETS: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f)
}
