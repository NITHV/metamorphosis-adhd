package io.github.nithv.braindump.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Short "how long ago" label for list rows: now, 5m, 3h, 2d, then a date. */
fun relativeTime(thenMs: Long, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val seconds = (nowMs - thenMs).coerceAtLeast(0) / 1000
    return when {
        seconds < 60 -> "now"
        seconds < 60 * 60 -> "${seconds / 60}m"
        seconds < 24 * 60 * 60 -> "${seconds / 3600}h"
        seconds < 7 * 24 * 60 * 60 -> "${seconds / 86_400}d"
        else -> {
            val then = Instant.ofEpochMilli(thenMs).atZone(zone)
            val now = Instant.ofEpochMilli(nowMs).atZone(zone)
            // Built per call so a language change while the app is open takes effect.
            val pattern = if (then.year == now.year) "d MMM" else "d MMM yyyy"
            DateTimeFormatter.ofPattern(pattern, Locale.getDefault()).format(then)
        }
    }
}
