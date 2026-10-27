package dev.vimivo.desktop.playback

import dev.vimivo.playback.ColorTransfer
import dev.vimivo.playback.EngineId
import dev.vimivo.playback.PlaybackDiagnosticsSnapshot

/** Typed reads of libmpv properties; the engine backs it with its handle, tests with a map. */
internal interface MpvProperties {
    fun string(name: String): String?
    fun flag(name: String): Boolean?
    fun long(name: String): Long?
    fun double(name: String): Double?
}

/** What the snapshot needs from the engine beyond libmpv's own properties. */
internal data class DiagnosticsContext(
    val engineId: EngineId,
    val mediaUri: String,
    val positionMillis: Long,
    val durationMillis: Long?,
    /** The hardware-decoding mode the user chose, shown when libmpv reports no active decoder. */
    val requestedHwdec: String,
    /** The video output libmpv reports nothing for (macOS renders through the render API). */
    val defaultRenderer: String,
    val softwareRendering: Boolean,
)

/** Builds the playback diagnostics shown in Info from libmpv's properties (V1 #8, #9). */
internal object MpvDiagnostics {
    /**
     * The picture's transfer function. libmpv reports it only in `video-params/gamma` (`pq`, `hlg`, `bt.1886`, …);
     * the old reading of the top-level `colormatrix` property never saw HDR (B3, evidence E5).
     */
    fun colorTransfer(gamma: String?): ColorTransfer = when (gamma?.lowercase()) {
        "pq" -> ColorTransfer.HDR10_PQ
        "hlg" -> ColorTransfer.HLG
        "bt.1886", "srgb", "gamma2.2", "gamma2.4", "linear" -> ColorTransfer.SDR_BT709
        else -> ColorTransfer.UNKNOWN
    }

    fun snapshot(props: MpvProperties, context: DiagnosticsContext): PlaybackDiagnosticsSnapshot {
        val decoder = decoder(props, context.requestedHwdec)
        val renderer = (props.string("current-vo") ?: context.defaultRenderer) +
            if (context.softwareRendering) " (software rendering, performance mode)" else ""
        return PlaybackDiagnosticsSnapshot(
            engineId = context.engineId,
            mediaUri = context.mediaUri,
            activeVideoCodec = decoder.videoCodec,
            activeAudioCodec = props.string("audio-codec") ?: props.string("audio-format"),
            activeDecoder = decoder.name,
            hwdecActive = decoder.hardware,
            hwdecMode = decoder.mode,
            activeRenderer = renderer,
            droppedFrames = props.long("drop-frame-count") ?: props.long("vo-delayed-frame-count") ?: 0L,
            totalFrames = props.long("estimated-frame-count") ?: 0L,
            avDriftMillis = (props.double("avsync") ?: 0.0) * MILLIS,
            estimatedFps = props.double("container-fps")?.toFloat() ?: 0f,
            cacheBufferBytes = props.long("demuxer-cache-state/cache-used")
                ?: props.long("demuxer-cache-state/bytes") ?: 0L,
            colorTransfer = colorTransfer(props.string("video-params/gamma")),
            positionMillis = context.positionMillis,
            durationMillis = context.durationMillis,
        )
    }

    private class Decoder(val videoCodec: String?, val name: String, val hardware: Boolean, val mode: String)

    /** The decoder in use: `hw:<api>` while libmpv decodes in hardware, else `sw:<codec>` (or its own decoder name). */
    private fun decoder(props: MpvProperties, requestedHwdec: String): Decoder {
        val videoCodec = props.string("video-codec") ?: props.string("video-format")
        val hwdec = props.string("hwdec-current")?.takeIf { it.isNotBlank() }
        val hardware = props.flag("hwdec-active") ?: (hwdec != null && hwdec != "no")
        val name = if (hardware) "hw:$hwdec" else (props.string("video-dec") ?: "sw:$videoCodec")
        return Decoder(videoCodec, name, hardware, hwdec ?: requestedHwdec)
    }

    private const val MILLIS = 1_000.0
}
