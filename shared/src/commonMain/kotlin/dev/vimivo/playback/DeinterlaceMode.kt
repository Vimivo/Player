package dev.vimivo.playback

/**
 * Deinterlacing choices (V1 #13). The filters are FFmpeg's LGPL ones, so libmpv engines (desktop, Android) share
 * them; `mcdeint` and `kerndeint` are GPL-only and not offered (ADR-004).
 */
enum class DeinterlaceMode(val label: String, val lavfiFilter: String?) {
    OFF("Off", null),

    /** Only frames flagged as interlaced are deinterlaced, with the better of the two spatial-temporal filters. */
    AUTO("Auto", "bwdif=mode=send_frame:parity=auto:deint=interlaced"),
    YADIF("Yadif", "yadif=mode=send_frame:parity=auto:deint=all"),
    BWDIF("Bwdif", "bwdif=mode=send_frame:parity=auto:deint=all"),
    W3FDIF("W3FDIF", "w3fdif=filter=complex:deint=all"),
    ESTDIF("Estdif", "estdif=mode=frame:deint=all"),
    ;

    /** The filter's name without options, e.g. `bwdif`. */
    val filterName: String? get() = lavfiFilter?.substringBefore('=')
}
