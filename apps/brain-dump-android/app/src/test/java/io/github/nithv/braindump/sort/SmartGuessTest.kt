package io.github.nithv.braindump.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Phone-only behaviour, where the app deliberately differs from chrono-node on the website. */
class SmartGuessTest {
    private val saturday2pm = LocalDateTime.of(2026, 10, 10, 14, 0)

    @Test
    fun numericDatesAreIgnoredBecauseTheyAreAmbiguous() {
        // 12/10 is 12 October in India and 10 December in the US. Guessing wrong is worse than not guessing.
        assertNull(findDate("dentist 12/10", saturday2pm))
    }

    @Test
    fun shortDayNamesNeedATimeOrAPreposition() {
        assertNull(findDate("I sat down and cried", saturday2pm))
        assertNull(findDate("sun is out", saturday2pm))
        assertEquals(LocalDateTime.of(2026, 10, 11, 9, 0), findDate("brunch on sun", saturday2pm))
    }

    @Test
    fun tonightWithATimeMeansTheEvening() {
        assertEquals(LocalDateTime.of(2026, 10, 10, 21, 0), findDate("tonight at 9", saturday2pm))
    }

    @Test
    fun impossibleDatesAreNotDates() {
        assertNull(findDate("feb 30", saturday2pm))
        assertNull(findDate("at 13pm", saturday2pm))
    }

    @Test
    fun aBareMonthNameIsNotADate() {
        assertNull(findDate("march to the shop", saturday2pm))
    }
}
