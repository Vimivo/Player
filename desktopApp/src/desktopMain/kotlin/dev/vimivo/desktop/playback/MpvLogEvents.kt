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

    /** The text of a fatal player message (libmpv's `[f]` level from the player core), else null. */
    fun fatalPlaybackText(message: MpvLogMessage): String? =
        message.text.takeIf { message.level == "fatal" && message.prefix == "cplayer" && it.isNotBlank() }

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

    /** CPU-only renderers by the names their drivers report (Mesa on Linux, WARP on Windows, Apple's on macOS). */
    private val SOFTWARE_RENDERER_NAMES =
        listOf("llvmpipe", "softpipe", "swrast", "microsoft basic render", "apple software renderer")
    private val GL_RENDERER = Regex("""GL_RENDERER='([^']*)'""")

    /** The renderer libmpv draws with, from its `GL_RENDERER='…'` line when the video output starts. */
    fun rendererName(message: MpvLogMessage): String? = GL_RENDERER.find(message.text)?.groupValues?.get(1)?.trim()

    /**
     * True when libmpv draws with a CPU-only renderer. Deterministic: the renderer name libmpv logs when its video
     * output starts (`GL_RENDERER='llvmpipe …'`, or the Direct3D adapter "Microsoft Basic Render Driver"); libmpv's
     * own "Suspected software renderer" warning is a fallback. On such a renderer libmpv's default scalers drop every
     * frame as late and the picture stays black.
     */
    fun isSoftwareRenderer(message: MpvLogMessage): Boolean {
        if (!message.prefix.startsWith("vo") && !message.prefix.startsWith("libmpv")) return false
        val text = message.text.lowercase()
        return "software renderer" in text || SOFTWARE_RENDERER_NAMES.any { it in text }
    }
}

/**
 * Remembers which renderer libmpv uses and whether it is CPU-only. libmpv's default scalers then drop every frame as
 * late and the picture stays black; its built-in `fast` profile keeps frames on time. Real GPUs keep the defaults.
 */
internal class MpvRendererWatch {
    @Volatile
    var softwareRenderer = false
        private set

    @Volatile
    var rendererName: String? = null
        private set

    /** Records [messages]; true only for the first batch that reports a software renderer. */
    fun firstSoftwareRendererReport(messages: List<MpvLogMessage>): Boolean {
        messages.firstNotNullOfOrNull(MpvLogEvents::rendererName)?.let { rendererName = it }
        if (softwareRenderer || messages.none(MpvLogEvents::isSoftwareRenderer)) return false
        softwareRenderer = true
        return true
    }

    /** What the Info panel shows, or null before the video output has started. */
    fun description(): String? = when {
        softwareRenderer -> "Software rendering — performance mode on" + (rendererName?.let { " ($it)" } ?: "")
        else -> rendererName
    }
}
