package dev.vimivo.desktop.playback

import com.sun.management.OperatingSystemMXBean
import dev.vimivo.core.model.MediaId
import dev.vimivo.core.model.MediaItem
import dev.vimivo.core.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit
import kotlin.test.Test

/**
 * Measures what desktop audio playback costs this process: CPU time and peak resident memory over a fixed window
 * after opening a track, the way the player opens one. Opt-in (it takes [WINDOW_MILLIS] and needs a long file):
 * `VIMIVO_FOOTPRINT_MEDIA=/path/song.mp3 ./gradlew :desktopApp:desktopTest --tests '*AudioPlaybackFootprint*'`.
 * Prints one `VIMIVO_FOOTPRINT` line; used for the before/after table in docs/evidence.
 */
class AudioPlaybackFootprintLiveTest {
    @Test
    fun reportsCpuAndPeakMemoryWhilePlayingAudio() {
        val file = System.getenv("VIMIVO_FOOTPRINT_MEDIA")?.let(::File)?.takeIf { it.isFile }
        if (file == null || runCatching { MpvNative.load() }.isFailure) {
            println("Skipping: set VIMIVO_FOOTPRINT_MEDIA to a long audio file (and install libmpv)")
            return
        }
        val os = ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean
        System.gc()
        val rssBefore = rssKb()
        val engine = MpvPlayerEngine()
        try {
            runBlocking {
                val cpuStart = os.processCpuTime
                val wallStart = System.nanoTime()
                val item = MediaItem(MediaId("footprint"), file.toPath().toUri().toString(), "footprint", MediaKind.AUDIO)
                engine.open(item).getOrThrow()
                engine.setVolume(0f).getOrThrow()
                engine.play().getOrThrow()
                withTimeout(OPEN_TIMEOUT_MILLIS) { engine.state.first { it.durationMillis != null } }
                val sampler = async(Dispatchers.IO) {
                    var peak = 0L
                    while (System.nanoTime() - wallStart < TimeUnit.MILLISECONDS.toNanos(WINDOW_MILLIS)) {
                        peak = maxOf(peak, rssKb())
                        delay(SAMPLE_MILLIS)
                    }
                    peak
                }
                val peakRss = sampler.await()
                val cpuMillis = (os.processCpuTime - cpuStart) / 1_000_000
                val wallMillis = (System.nanoTime() - wallStart) / 1_000_000
                println(
                    "VIMIVO_FOOTPRINT cpu_ms=$cpuMillis wall_ms=$wallMillis " +
                        "avg_cpu_pct=${"%.1f".format(cpuMillis * 100.0 / wallMillis)} " +
                        "rss_before_mb=${rssBefore / 1024} peak_rss_mb=${peakRss / 1024} " +
                        "delta_mb=${(peakRss - rssBefore) / 1024}",
                )
            }
        } finally {
            engine.close()
        }
    }

    private fun rssKb(): Long {
        val pid = ProcessHandle.current().pid()
        val process = ProcessBuilder("ps", "-o", "rss=", "-p", pid.toString()).redirectErrorStream(true).start()
        val text = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        return text.toLongOrNull() ?: error("ps gave no RSS: $text")
    }

    private companion object {
        const val WINDOW_MILLIS = 20_000L
        const val SAMPLE_MILLIS = 200L
        const val OPEN_TIMEOUT_MILLIS = 5_000L
    }
}
