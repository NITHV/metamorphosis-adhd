package io.github.nithv.braindump

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
    @Test
    fun matchesTheWebsiteBoundaries() {
        assertEquals("Hey, night owl 🦉", greeting(0))
        assertEquals("Hey, night owl 🦉", greeting(4))
        assertEquals("Good morning 👋", greeting(5))
        assertEquals("Good morning 👋", greeting(11))
        assertEquals("Good afternoon 👋", greeting(12))
        assertEquals("Good afternoon 👋", greeting(16))
        assertEquals("Good evening 👋", greeting(17))
        assertEquals("Good evening 👋", greeting(23))
    }
}
