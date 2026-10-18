package dev.vimivo.desktop.integration.mpris

import dev.vimivo.playback.PlaybackControlPolicy

/** Parses MPRIS method calls and property writes into [MprisCommand]s. */
internal object MprisRequests {
    /** Maps an incoming player method call to a command; null for unknown methods. */
    fun playerCommand(member: String, args: List<Any>): MprisCommand? = when (member) {
        "PlayPause" -> MprisCommand.PlayPause
        "Play" -> MprisCommand.Play
        "Pause" -> MprisCommand.Pause
        "Stop" -> MprisCommand.Stop
        "Next" -> MprisCommand.Next
        "Previous" -> MprisCommand.Previous
        "Seek" -> (args.firstOrNull() as? Long)?.let(MprisCommand::Seek)
        "SetPosition" -> {
            val track = args.getOrNull(0) as? String
            val position = args.getOrNull(1) as? Long
            if (track != null && position != null) MprisCommand.SetPosition(track, position) else null
        }
        else -> null
    }

    /** Maps a writable player property to a command; null when the property or value type is not writable. */
    fun propertyCommand(property: String, value: Any?): MprisCommand? = when (property) {
        "Volume" -> (value as? Double)?.let(MprisCommand::SetVolume)
        "Rate" -> (value as? Double)?.let(MprisCommand::SetRate)
        "Shuffle" -> (value as? Boolean)?.let(MprisCommand::SetShuffle)
        "LoopStatus" -> (value as? String)?.let(MprisCommand::SetLoopStatus)
        else -> null
    }

    /** Linear MPRIS volume to the engine's 0..max range. */
    fun engineVolume(mprisVolume: Double, maxVolume: Float): Float =
        mprisVolume.toFloat().coerceIn(0f, maxVolume.coerceAtLeast(PlaybackControlPolicy.UNITY_VOLUME))
}
