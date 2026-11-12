package dev.vimivo.playback

/** How hard the renderer works to scale the picture. Higher looks sharper and uses more GPU. */
enum class UpscaleQuality(val label: String) {
    STANDARD("Standard"),
    HIGH("High"),
    ULTRA("Ultra"),
}

/** How bright HDR pictures are mapped onto a screen that cannot show their full range. */
enum class ToneMapping(val label: String) {
    AUTO("Automatic"),
    BT2390("BT.2390"),
    HABLE("Hable"),
    MOBIUS("Mobius"),
    REINHARD("Reinhard"),
}

/**
 * Picture and audio-output quality options that go beyond brightness and contrast (desktop renderer).
 * [smoothMotion] resamples frames to the display rate; [exclusiveAudio] takes the output device for bit-exact sound.
 */
data class VideoQualitySettings(
    val upscale: UpscaleQuality = UpscaleQuality.HIGH,
    val deband: Boolean = false,
    val smoothMotion: Boolean = false,
    val toneMapping: ToneMapping = ToneMapping.AUTO,
    val exclusiveAudio: Boolean = false,
) {
    /** The renderer options for these settings as name/value pairs, in the order they should be applied. */
    fun toMpvOptions(): List<Pair<String, String>> {
        val scaler = when (upscale) {
            UpscaleQuality.STANDARD -> "bilinear"
            UpscaleQuality.HIGH -> "spline36"
            UpscaleQuality.ULTRA -> "ewa_lanczossharp"
        }
        val downscaler = if (upscale == UpscaleQuality.STANDARD) "bilinear" else "mitchell"
        return listOf(
            "scale" to scaler,
            "cscale" to scaler,
            "dscale" to downscaler,
            "correct-downscaling" to yesNo(upscale == UpscaleQuality.ULTRA),
            "deband" to yesNo(deband),
            "interpolation" to yesNo(smoothMotion),
            "video-sync" to if (smoothMotion) "display-resample" else "audio",
            "tscale" to if (smoothMotion) "oversample" else "mitchell",
            "tone-mapping" to when (toneMapping) {
                ToneMapping.AUTO -> "auto"
                ToneMapping.BT2390 -> "bt.2390"
                ToneMapping.HABLE -> "hable"
                ToneMapping.MOBIUS -> "mobius"
                ToneMapping.REINHARD -> "reinhard"
            },
            "audio-exclusive" to yesNo(exclusiveAudio),
        )
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
