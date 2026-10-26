package dev.vimivo.desktop.player

import java.io.File

/**
 * Command-line launch request: `vimivo [--start=HH:MM:SS] [--fullscreen] file…`. Also what a second
 * instance forwards to the running one. Unknown options are ignored rather than mistaken for files.
 */
internal data class LaunchArguments(
    val files: List<File> = emptyList(),
    val startPositionMillis: Long? = null,
    val endPositionMillis: Long? = null,
    val fullscreen: Boolean = false,
    val smokeTest: Boolean = false,
    val volume: Float? = null,
    val speed: Float? = null,
    val subtitleFile: File? = null,
    val audioTrack: String? = null,
    val loop: Boolean = false,
    val enqueue: Boolean = false,
    val audioOnly: Boolean = false,
    val showHelp: Boolean = false,
) {
    companion object {
        private const val MAX_PARTS = 3
        private const val SECONDS_PER_MINUTE = 60L
        private const val MILLIS_PER_SECOND = 1_000L

        fun parse(args: Array<String>): LaunchArguments {
            var start: Long? = null
            var end: Long? = null
            var fullscreen = false
            var smokeTest = false
            var volume: Float? = null
            var speed: Float? = null
            var subFile: File? = null
            var audioTrack: String? = null
            var loop = false
            var enqueue = false
            var audioOnly = false
            var showHelp = false
            val files = mutableListOf<File>()

            args.forEach { arg ->
                when {
                    arg == "--help" || arg == "-h" -> showHelp = true
                    arg == "--fullscreen" -> fullscreen = true
                    arg == "--smoke-test" -> smokeTest = true
                    arg == "--loop" -> loop = true
                    arg == "--enqueue" -> enqueue = true
                    arg == "--audio-only" -> audioOnly = true
                    arg.startsWith("--start=") -> start = parseTime(arg.substringAfter('='))
                    arg.startsWith("--end=") -> end = parseTime(arg.substringAfter('='))
                    arg.startsWith("--volume=") -> volume = arg.substringAfter('=').toFloatOrNull()?.div(100f)
                    arg.startsWith("--speed=") -> speed = arg.substringAfter('=').toFloatOrNull()
                    arg.startsWith("--sub=") -> subFile = File(arg.substringAfter('='))
                    arg.startsWith("--audio-track=") -> audioTrack = arg.substringAfter('=')
                    // macOS adds -psn_0_12345 when launched from Finder on older systems.
                    arg.startsWith("-") -> Unit
                    else -> files += File(arg)
                }
            }
            return LaunchArguments(
                files = files,
                startPositionMillis = start,
                endPositionMillis = end,
                fullscreen = fullscreen,
                smokeTest = smokeTest,
                volume = volume,
                speed = speed,
                subtitleFile = subFile,
                audioTrack = audioTrack,
                loop = loop,
                enqueue = enqueue,
                audioOnly = audioOnly,
                showHelp = showHelp,
            )
        }

        fun helpMessage(): String = """
            Vimivo Media Player
            Usage: vimivo [OPTIONS] [FILE...]

            Options:
              --help, -h               Show this help message and exit
              --fullscreen             Start playback in full screen
              --start=TIME             Start playback at TIME (seconds, MM:SS, or HH:MM:SS)
              --end=TIME               Stop playback at TIME (seconds, MM:SS, or HH:MM:SS)
              --volume=PERCENT         Initial volume (0-200)
              --speed=FACTOR           Playback speed multiplier (e.g. 1.25)
              --sub=FILE               Load external subtitle file
              --audio-track=TRACK      Select audio track by ID or index
              --loop                   Loop current file or playlist
              --enqueue                Add files to queue instead of replacing
              --audio-only             Play video file in audio-only mode
        """.trimIndent()

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
