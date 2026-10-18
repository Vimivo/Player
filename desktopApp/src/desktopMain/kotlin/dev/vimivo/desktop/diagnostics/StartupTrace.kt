package dev.vimivo.desktop.diagnostics

import java.time.Duration
import java.time.Instant

/**
 * Startup milestones relative to process start, printed as `VIMIVO_STARTUP <milestone>=<ms>` when
 * `VIMIVO_STARTUP_TRACE=1`. Used to measure the launch budgets on each OS; silent otherwise.
 */
internal object StartupTrace {
    private val enabled = System.getenv("VIMIVO_STARTUP_TRACE") == "1"
    private val processStart: Instant? = ProcessHandle.current().info().startInstant().orElse(null)
    private val reported = mutableSetOf<String>()

    fun mark(milestone: String) {
        if (!enabled || processStart == null) return
        synchronized(reported) { if (!reported.add(milestone)) return }
        println("VIMIVO_STARTUP $milestone=${Duration.between(processStart, Instant.now()).toMillis()}")
    }
}
