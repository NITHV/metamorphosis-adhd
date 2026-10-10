package io.github.nithv.braindump.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class RelativeTimeTest {
    private val utc = ZoneOffset.UTC
    private val now = ZonedDateTime.of(2026, 10, 11, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
    private fun ago(seconds: Long) = relativeTime(now - seconds * 1000, now, utc)

    @Test
    fun shortLabels() {
        assertEquals("now", ago(0))
        assertEquals("now", ago(59))
        assertEquals("1m", ago(60))
        assertEquals("59m", ago(59 * 60))
        assertEquals("1h", ago(60 * 60))
        assertEquals("23h", ago(23 * 3600))
        assertEquals("1d", ago(24 * 3600))
        assertEquals("6d", ago(6 * 86_400))
    }

    @Test
    fun olderDumpsShowADate() {
        assertEquals("1 Oct", ago(10 * 86_400))
        assertEquals("11 Oct 2025", ago(365 * 86_400))
    }

    @Test
    fun clockSkewIntoTheFutureShowsNow() {
        assertEquals("now", relativeTime(now + 5_000, now, utc))
    }
}
