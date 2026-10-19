package dev.vimivo.desktop.playback

import com.sun.jna.Native
import com.sun.jna.Pointer

/** One libmpv log line, as delivered by `MPV_EVENT_LOG_MESSAGE`. */
internal data class MpvLogMessage(val prefix: String, val level: String, val text: String)

/** Reads libmpv's event queue without blocking (client API `mpv_wait_event` with a zero timeout). */
internal object MpvLogEvents {
    private const val EVENT_NONE = 0
    private const val EVENT_LOG_MESSAGE = 2

    /** Offset of `data` in `struct mpv_event` on the 64-bit targets Vimivo ships (int, int, uint64_t, pointer). */
    private const val EVENT_DATA_OFFSET = 16L

    /** At most this many events per drain, so a flood never stalls the caller. */
    private const val MAX_EVENTS_PER_DRAIN = 64

    /** Takes every queued event and returns the log messages among them. Not thread-safe: one caller at a time. */
    fun drain(native: MpvNative, context: Pointer): List<MpvLogMessage> {
        val messages = mutableListOf<MpvLogMessage>()
        repeat(MAX_EVENTS_PER_DRAIN) {
            val event = native.mpv_wait_event(context, 0.0) ?: return messages
            // struct mpv_event { int event_id; int error; uint64_t reply_userdata; void *data; }
            when (event.getInt(0)) {
                EVENT_NONE -> return messages
                EVENT_LOG_MESSAGE -> event.getPointer(EVENT_DATA_OFFSET)?.let { data ->
                    // struct mpv_event_log_message { const char *prefix; const char *level; const char *text; ... }
                    messages += MpvLogMessage(
                        prefix = data.getPointer(0)?.getString(0).orEmpty(),
                        level = data.getPointer(Native.POINTER_SIZE.toLong())?.getString(0).orEmpty(),
                        text = data.getPointer(2L * Native.POINTER_SIZE)?.getString(0).orEmpty().trim(),
                    )
                }
            }
        }
        return messages
    }

    /**
     * libmpv's GPU renderer warns "Suspected software renderer or indirect context" when only a CPU OpenGL
     * implementation exists (no GPU drivers, VMs, remote desktops, CI). Its default high-quality scalers are then too
     * slow: every frame arrives late and is dropped, and the window stays black while audio plays.
     */
    fun isSoftwareRenderer(message: MpvLogMessage): Boolean =
        message.prefix.startsWith("vo/gpu") && "software renderer" in message.text.lowercase()
}
