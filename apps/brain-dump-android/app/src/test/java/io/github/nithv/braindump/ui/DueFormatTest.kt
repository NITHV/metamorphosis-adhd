package io.github.nithv.braindump.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class DueFormatTest {
    private val now = LocalDateTime.of(2026, 10, 10, 14, 0) // Saturday
    private fun fmt(due: LocalDateTime) = formatDue(due, now, Locale.UK)

    @Test
    fun nineAmMeansNoTimeGiven() {
        assertEquals("Tomorrow", fmt(LocalDateTime.of(2026, 10, 11, 9, 0)))
    }

    @Test
    fun nearbyDaysReadNaturally() {
        assertEquals("today 5:00 pm", fmt(LocalDateTime.of(2026, 10, 10, 17, 0)).lowercase(Locale.UK)) // am/pm case varies by JDK
        assertEquals("Yesterday", fmt(LocalDateTime.of(2026, 10, 9, 9, 0)))
        assertEquals("Wednesday", fmt(LocalDateTime.of(2026, 10, 14, 9, 0)))
    }

    @Test
    fun laterDaysShowTheDate() {
        assertEquals("Mon 26 Oct", fmt(LocalDateTime.of(2026, 10, 26, 9, 0)))
        assertEquals("mon 26 oct, 3:30 pm", fmt(LocalDateTime.of(2026, 10, 26, 15, 30)).lowercase(Locale.UK))
    }
}
