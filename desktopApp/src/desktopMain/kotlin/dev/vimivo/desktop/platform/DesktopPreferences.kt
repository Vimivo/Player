package dev.vimivo.desktop.platform

import dev.vimivo.desktop.playback.HotkeyPreset
import dev.vimivo.desktop.player.ui.DesktopToolbarConfig
import dev.vimivo.playback.ToneMapping
import dev.vimivo.playback.UpscaleQuality
import dev.vimivo.playback.VideoQualitySettings
import java.io.File
import java.io.IOException
import java.util.Properties

/** The main window's last size and, when known, position. */
internal data class SavedWindow(val width: Int, val height: Int, val x: Int?, val y: Int?)

internal const val MIN_WINDOW_WIDTH = 800
internal const val MIN_WINDOW_HEIGHT = 520

/**
 * The few desktop choices that are not library data (toolbar buttons, video quality), kept in one small text file in the
 * per-user data folder. A missing or damaged file just means defaults; saving never throws into the UI.
 */
internal class DesktopPreferences(private val file: File) {
    private val values = Properties().also { props ->
        if (file.isFile) runCatching { file.inputStream().use(props::load) }
    }

    fun loadToolbar(): DesktopToolbarConfig {
        val d = DesktopToolbarConfig()
        return DesktopToolbarConfig(
            showAudioOnly = flag("toolbar.audioOnly", d.showAudioOnly),
            showPip = flag("toolbar.pip", d.showPip),
            showMiniPlayer = flag("toolbar.mini", d.showMiniPlayer),
            showRecord = flag("toolbar.record", d.showRecord),
            showSettings = flag("toolbar.settings", d.showSettings),
            showFullscreen = flag("toolbar.fullscreen", d.showFullscreen),
        )
    }

    fun saveToolbar(config: DesktopToolbarConfig) = update {
        put("toolbar.audioOnly", config.showAudioOnly.toString())
        put("toolbar.pip", config.showPip.toString())
        put("toolbar.mini", config.showMiniPlayer.toString())
        put("toolbar.record", config.showRecord.toString())
        put("toolbar.settings", config.showSettings.toString())
        put("toolbar.fullscreen", config.showFullscreen.toString())
    }

    fun loadVideoQuality(): VideoQualitySettings {
        val d = VideoQualitySettings()
        return VideoQualitySettings(
            upscale = enumValue("quality.upscale", d.upscale),
            deband = flag("quality.deband", d.deband),
            smoothMotion = flag("quality.smooth", d.smoothMotion),
            toneMapping = enumValue("quality.tone", d.toneMapping),
            exclusiveAudio = flag("quality.exclusive", d.exclusiveAudio),
        )
    }

    fun saveVideoQuality(settings: VideoQualitySettings) = update {
        put("quality.upscale", settings.upscale.name)
        put("quality.deband", settings.deband.toString())
        put("quality.smooth", settings.smoothMotion.toString())
        put("quality.tone", settings.toneMapping.name)
        put("quality.exclusive", settings.exclusiveAudio.toString())
    }

    fun loadHotkeyPreset(): HotkeyPreset = enumValue("hotkeys.preset", HotkeyPreset.VIMIVO)

    fun saveHotkeyPreset(preset: HotkeyPreset) = update { put("hotkeys.preset", preset.name) }

    /** Where the main window was last left, in pixels, or null when never saved or no longer on any screen. */
    fun loadWindow(): SavedWindow? {
        val width = values.getProperty("window.width")?.toIntOrNull() ?: return null
        val height = values.getProperty("window.height")?.toIntOrNull() ?: return null
        if (width < MIN_WINDOW_WIDTH || height < MIN_WINDOW_HEIGHT) return null
        val x = values.getProperty("window.x")?.toIntOrNull()
        val y = values.getProperty("window.y")?.toIntOrNull()
        return SavedWindow(width, height, x, y)
    }

    fun saveWindow(window: SavedWindow) = update {
        put("window.width", window.width.toString())
        put("window.height", window.height.toString())
        window.x?.let { put("window.x", it.toString()) }
        window.y?.let { put("window.y", it.toString()) }
    }

    private fun flag(key: String, default: Boolean): Boolean = values.getProperty(key)?.toBooleanStrictOrNull() ?: default

    private inline fun <reified T : Enum<T>> enumValue(key: String, default: T): T =
        values.getProperty(key)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

    @Synchronized
    private fun update(change: Properties.() -> Unit) {
        values.change()
        try {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.outputStream().use { values.store(it, "Vimivo preferences") }
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        } catch (_: IOException) {
            // Preferences are a convenience; a read-only disk must not break playback.
        }
    }
}
