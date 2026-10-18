package dev.vimivo.desktop.player

import java.io.File

/**
 * Command-line launch request: `vimivo [--start=HH:MM:SS] [--fullscreen] file…`. Also what a second
 * instance forwards to the running one. Unknown options are ignored rather than mistaken for files.
 */
internal data class LaunchArguments(
    val files: List<File> = emptyList(),
    val startPositionMillis: Long? = null,
    val fullscreen: Boolean = false,
    val smokeTest: Boolean = false,
) {
    companion object {
        private const val MAX_PARTS = 3
        private const val SECONDS_PER_MINUTE = 60L
        private const val MILLIS_PER_SECOND = 1_000L

        fun parse(args: Array<String>): LaunchArguments {
            var start: Long? = null
            var fullscreen = false
            var smokeTest = false
            val files = mutableListOf<File>()
            args.forEach { arg ->
                when {
                    arg == "--fullscreen" -> fullscreen = true
                    arg == "--smoke-test" -> smokeTest = true
                    arg.startsWith("--start=") -> start = parseTime(arg.substringAfter('='))
                    // macOS adds -psn_0_12345 when launched from Finder on older systems.
                    arg.startsWith("-") -> Unit
                    else -> files += File(arg)
                }
            }
            return LaunchArguments(files, start, fullscreen, smokeTest)
        }

        /** Accepts `90`, `1:30`, `01:02:03` and fractional seconds like `1:30.5`; null when malformed. */
        fun parseTime(value: String): Long? {
            val parts = value.trim().split(':')
            val seconds = parts.last().toDoubleOrNull()?.takeIf { it >= 0 }
            val wholeUnits = parts.dropLast(1).map { part -> part.toLongOrNull()?.takeIf { it >= 0 } }
            val valid = parts.size <= MAX_PARTS && seconds != null && wholeUnits.none { it == null }
            if (!valid) return null
            val minutesTotal = wholeUnits.fold(0L) { acc, unit -> acc * SECONDS_PER_MINUTE + checkNotNull(unit) }
            val fractionalMillis = (checkNotNull(seconds) * MILLIS_PER_SECOND).toLong()
            return minutesTotal * SECONDS_PER_MINUTE * MILLIS_PER_SECOND + fractionalMillis
        }
    }
}
