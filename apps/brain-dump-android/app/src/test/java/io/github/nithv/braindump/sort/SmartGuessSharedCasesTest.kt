package io.github.nithv.braindump.sort

import io.github.nithv.braindump.data.Kind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDateTime

/**
 * The contract with the website: shared/smart-guess-cases.json. The website runs the same file
 * (apps/brain-dump/src/lib/smart-guess.test.ts). Every failing case is listed, not just the first,
 * so one run shows the whole picture. (Robolectric only to get a real org.json on the JVM.)
 */
@RunWith(RobolectricTestRunner::class)
class SmartGuessSharedCasesTest {
    private val cases = JSONObject(File(System.getProperty("brainDump.sharedCases")!!).readText())
    private val now = LocalDateTime.parse(cases.getString("now"))

    private fun JSONObject.list(name: String) = getJSONArray(name).let { a -> (0 until a.length()).map(a::getJSONObject) }

    private fun checkAll(name: String, failures: List<String>, total: Int) {
        assertEquals("$name: ${failures.size} of $total cases failed:\n" + failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun dates() {
        val all = cases.list("dates")
        val failures = all.mapNotNull { c ->
            val text = c.getString("text")
            val expected = if (c.isNull("due")) null else LocalDateTime.parse(c.getString("due"))
            val actual = findDate(text, now)
            if (actual == expected) null else "  \"$text\": expected $expected, got $actual"
        }
        checkAll("dates", failures, all.size)
    }

    @Test
    fun kinds() {
        val all = cases.list("kinds")
        val failures = all.mapNotNull { c ->
            val text = c.getString("text")
            val expected = if (c.isNull("kind")) null else Kind.valueOf(c.getString("kind").uppercase())
            val actual = guessKind(text, now)
            if (actual == expected) null else "  \"$text\": expected $expected, got $actual"
        }
        checkAll("kinds", failures, all.size)
    }

    @Test
    fun splits() {
        val all = cases.list("splits")
        val failures = all.mapNotNull { c ->
            val text = c.getString("text")
            val parts = c.getJSONArray("parts")
            val expected = (0 until parts.length()).map(parts::getString)
            val actual = splitDump(text)
            if (actual == expected) null else "  ${JSONObject.quote(text)}: expected $expected, got $actual"
        }
        checkAll("splits", failures, all.size)
    }

    @Test
    fun theCasesFileIsNotEmpty() {
        // Guards against a broken path or a truncated file making every check pass vacuously.
        check(cases.list("dates").size >= 40 && cases.list("kinds").size >= 10 && cases.list("splits").size >= 5)
    }
}
