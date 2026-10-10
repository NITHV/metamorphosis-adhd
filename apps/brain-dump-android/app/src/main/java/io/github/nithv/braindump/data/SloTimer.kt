package io.github.nithv.braindump.data

import android.os.SystemClock
import android.util.Log

/**
 * Times the operations our reliability targets (SLOs, design doc §6) are about.
 * For now it logs to logcat under the "BrainDump/SLO" tag; the on-phone diagnostics screen
 * in milestone N7 will keep a history.
 */
object SloTimer {
    private const val TAG = "BrainDump/SLO"

    /** Text dump: tap "Dump it" → saved. Target: 99% under 300 ms. */
    const val SAVE_TEXT_TARGET_MS = 300L

    inline fun <T> measure(name: String, targetMs: Long, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        try {
            return block()
        } finally {
            record(name, SystemClock.elapsedRealtime() - start, targetMs)
        }
    }

    fun record(name: String, ms: Long, targetMs: Long) {
        // Logging must never break the thing being measured.
        runCatching {
            if (ms > targetMs) Log.w(TAG, "$name took $ms ms (target $targetMs ms)")
            else Log.i(TAG, "$name $ms ms")
        }
    }
}
