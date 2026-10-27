package dev.vimivo.desktop.performance

import kotlin.test.assertTrue

/**
 * Wall-clock budgets for benchmarks. Shared CI runners stall unpredictably (a 100 ms budget failed at 160 ms on a busy
 * Windows runner), so a budget is always reported as a `VIMIVO_PERF` line but fails the test only in a dedicated
 * performance run with `VIMIVO_PERF_GATE=1`. Correctness checks in the same tests stay gating.
 */
internal object PerformanceBudget {
    private val gating = System.getenv("VIMIVO_PERF_GATE") == "1"

    fun check(label: String, actualMillis: Number, budgetMillis: Number) {
        val within = actualMillis.toDouble() <= budgetMillis.toDouble()
        println("VIMIVO_PERF ${if (within) "OK  " else "OVER"} $label: $actualMillis ms (budget $budgetMillis ms)")
        if (gating) assertTrue(within, "$label took $actualMillis ms, budget $budgetMillis ms")
    }
}
