package org.psilynx.psikit.ftc

/**
 * Global knobs for reducing logging overhead.
 *
 * Notes on semantics:
 * - Skipping `table.put(...)` for a key does NOT delete it; LogTable retains the last value.
 * - This makes rate limiting safe: "in between" loops will still observe the last logged value.
 */
object FtcLogTuning {
    /** If true, issue one bulk read per hub at the start of each loop (after clearBulkCache). */
    @JvmField var prefetchBulkDataEachLoop: Boolean = false

    /**
     * If > 0, wrappers may sample *non-bulk* (generally I2C / ADC / readback) data at this period
     * and skip writes in between.
     */
    @JvmField var nonBulkReadPeriodSec: Double = 0.0
}
