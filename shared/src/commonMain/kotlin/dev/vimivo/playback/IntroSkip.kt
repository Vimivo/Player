package dev.vimivo.playback

/**
 * Where "skip intro" should land. It uses the file's own chapters when they point at an opening (a chapter called
 * intro, opening, recap, ... or a short first chapter), and otherwise falls back to a fixed jump forward.
 */
object IntroSkip {
    const val FALLBACK_JUMP_MILLIS: Long = 85_000L
    private const val MAX_INTRO_END_MILLIS = 5 * 60_000L
    private const val MIN_CHAPTERS_FOR_SHORT_FIRST = 3
    private const val END_MARGIN_MILLIS = 1_000L
    private val introTitle = Regex("(?i)\\b(intro|opening|op|recap|previously|prologue|cold open)\\b")

    fun target(positionMillis: Long, chapters: List<ChapterInfo>, durationMillis: Long?): Long {
        val sorted = chapters.sortedBy { it.startPositionMillis }
        val current = sorted.indexOfLast { it.startPositionMillis <= positionMillis }
        if (current >= 0 && current + 1 < sorted.size) {
            val next = sorted[current + 1].startPositionMillis
            val inIntro = introTitle.containsMatchIn(sorted[current].title)
            val shortFirst = current == 0 && next <= MAX_INTRO_END_MILLIS && sorted.size >= MIN_CHAPTERS_FOR_SHORT_FIRST
            if ((inIntro || shortFirst) && next > positionMillis) return next
        }
        val jump = positionMillis + FALLBACK_JUMP_MILLIS
        val limit = durationMillis?.takeIf { it > 0 }?.minus(END_MARGIN_MILLIS) ?: return jump
        return jump.coerceAtMost(limit).coerceAtLeast(positionMillis)
    }
}
