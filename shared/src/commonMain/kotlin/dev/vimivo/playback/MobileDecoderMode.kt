package dev.vimivo.playback

/**
 * Mobile decoding engine and hardware acceleration mode matching MX Player's signature chip.
 * - HW: Native platform hardware decoder (Media3 + MediaCodec on Android; AVFoundation on iOS)
 * - HW+: Advanced hardware decoder using libmpv's hardware acceleration bridge
 * - SW: Pure software decoding (libmpv / libavcodec CPU decoding)
 */
enum class MobileDecoderMode(val chipLabel: String, val displayName: String) {
    HW("HW", "Hardware Decoder (HW)"),
    HW_PLUS("HW+", "Hardware+ Decoder (HW+)"),
    SW("SW", "Software Decoder (SW)");

    companion object {
        fun fromString(value: String?): MobileDecoderMode = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true) || it.chipLabel.equals(value, ignoreCase = true)
        } ?: HW
    }
}

/**
 * Persists per-file decoder choices and auto-fallback decisions across sessions.
 */
interface DecoderPreferenceStore {
    fun getDecoderForMedia(uri: String): MobileDecoderMode?
    fun setDecoderForMedia(uri: String, mode: MobileDecoderMode)
    fun getFallbackDecoderForMedia(uri: String): MobileDecoderMode?
    fun recordFallback(uri: String, mode: MobileDecoderMode)
    fun clearPreference(uri: String)
}

/**
 * Shared in-memory and persistent decoder preference repository.
 */
class InMemoryDecoderPreferenceStore : DecoderPreferenceStore {
    private val manualPreferences = mutableMapOf<String, MobileDecoderMode>()
    private val fallbackMemory = mutableMapOf<String, MobileDecoderMode>()

    override fun getDecoderForMedia(uri: String): MobileDecoderMode? =
        manualPreferences[uri] ?: fallbackMemory[uri]

    override fun setDecoderForMedia(uri: String, mode: MobileDecoderMode) {
        manualPreferences[uri] = mode
    }

    override fun getFallbackDecoderForMedia(uri: String): MobileDecoderMode? =
        fallbackMemory[uri]

    override fun recordFallback(uri: String, mode: MobileDecoderMode) {
        fallbackMemory[uri] = mode
    }

    override fun clearPreference(uri: String) {
        manualPreferences.remove(uri)
        fallbackMemory.remove(uri)
    }

    companion object {
        val shared: DecoderPreferenceStore = InMemoryDecoderPreferenceStore()
    }
}
