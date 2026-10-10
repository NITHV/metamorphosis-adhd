package io.github.nithv.braindump

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test
    fun matchesTheWebsiteBoundaries() {
        assertEquals("Good morning", greeting(0))
        assertEquals("Good morning", greeting(11))
        assertEquals("Good afternoon", greeting(12))
        assertEquals("Good afternoon", greeting(16))
        assertEquals("Good evening", greeting(17))
        assertEquals("Good evening", greeting(23))
    }
}
