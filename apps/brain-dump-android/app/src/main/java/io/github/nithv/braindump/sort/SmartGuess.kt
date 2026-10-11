package io.github.nithv.braindump.sort

import io.github.nithv.braindump.data.Kind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

// The phone's copy of the website's sorting helpers (apps/brain-dump/src/lib/smart-guess.ts).
// The website uses the chrono-node library for dates; this is a smaller hand-written finder for the
// phrases people actually type. Both must pass shared/smart-guess-cases.json, so they agree.
// Rules only pre-select a chip; the person always decides.

private val WORRY = Regex(
    """\b(worr(y|ied|ying)|anxious|anxiety|scared|afraid|nervous|stress(ed|ful)?|panic|dread|overwhelm(ed|ing)?|what if)\b""",
    RegexOption.IGNORE_CASE,
)
private val IDEA = Regex(
    """^(idea|thought)\b|\b(maybe (we|i) (could|should)|what about|could try|would be (cool|nice|fun)|someday|wouldn'?t it be|how about)\b""",
    RegexOption.IGNORE_CASE,
)
private val TASK_VERBS = Regex(
    """^(buy|call|email|text|message|reply|fix|book|pay|send|finish|clean|wash|write|read|order|cancel|schedule|submit|return|pick up|drop off|renew|update|check|ask|tell|make|get|do|go|print|sign|file|apply|prepare|plan|review|cook|water|take|bring|move|sort|organi[sz]e|charge|install|backup|back up)\b""",
    RegexOption.IGNORE_CASE,
)

fun guessKind(text: String, now: LocalDateTime = LocalDateTime.now()): Kind? {
    val t = text.trim()
    return when {
        WORRY.containsMatchIn(t) -> Kind.WORRY
        findDate(t, now) != null -> Kind.REMINDER
        IDEA.containsMatchIn(t) -> Kind.IDEA
        TASK_VERBS.containsMatchIn(t) -> Kind.TASK
        else -> null
    }
}

private val BULLET = Regex("""^\s*([-*•]|\d+[.)])\s+""")
private val SENTENCE_END = Regex("""(?<=[.!?])\s+(?=[A-Z0-9"'])""")

/** Splits a ramble into separate thoughts: by line, bullet or number, else by sentence. */
fun splitDump(text: String): List<String> {
    fun clean(parts: List<String>) = parts.map { it.replace(BULLET, "").trim() }.filter { it.length > 1 }
    val lines = clean(text.split(Regex("""\r?\n+""")))
    if (lines.size > 1) return lines
    val sentences = clean(text.split(SENTENCE_END))
    return if (sentences.size > 1) sentences else listOf(text.trim())
}

// ---------------------------------------------------------------------------------------------
// Date finder
// ---------------------------------------------------------------------------------------------

/** A dump's date with no time given ("tomorrow") means the morning, not "this exact minute tomorrow". */
val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

private const val I = "(?i)"

/** "in 2 hours", "in a week". Minutes and hours give an exact time; longer spans give a day. */
private val IN_SPAN = Regex("""$I\bin\s+(a|an|\d{1,3})\s+(mins?|minutes?|hrs?|hours?|days?|weeks?|months?|years?)\b""")

private val NEXT_SPAN = Regex("""$I\bnext\s+(week|month|year)\b""")

private val DAY_WORD = Regex("""$I\b(today|tonight|tomorrow)\b""")

private const val DAY_NAMES = "monday|tuesday|wednesday|thursday|friday|saturday|sunday"
private const val DAY_SHORT = "mon|tue|wed|thu|thur|thurs|fri|sat|sun"

/** A weekday. Short forms ("sat", "sun", "wed") are also ordinary words, so they need a time too. */
private val WEEKDAY = Regex("""$I\b(?:(on|by|next|this)\s+)?($DAY_NAMES|$DAY_SHORT)\b""")

private const val MONTHS =
    "jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"

/** "Oct 12", "October 12th 2027". */
private val MONTH_DAY = Regex("""$I\b($MONTHS)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4})\b)?""")

/** "12 October", "2nd nov 2027". */
private val DAY_MONTH = Regex("""$I\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:of\s+)?($MONTHS)\b\.?(?:\s+(\d{4})\b)?""")

/** "5pm", "3:15 pm", "3.30pm". */
private val TIME_AMPM = Regex("""$I\b(\d{1,2})(?:[:.](\d{2}))?\s*([ap])\.?m\b\.?""")

/** "17:00", "at 9:30" (24-hour clock). */
private val TIME_24H = Regex("""\b(\d{1,2}):(\d{2})\b""")

/** "at 7": a bare hour only counts after "at". */
private val TIME_AT_HOUR = Regex("""$I\bat\s+(\d{1,2})\b(?![:.]\d)""")

private val TIME_WORD = Regex("""$I\b(noon|midnight)\b""")

private fun monthNumber(name: String): Int {
    val m = name.lowercase()
    return listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
        .indexOfFirst { m.startsWith(it) } + 1
}

private fun dayOfWeek(name: String): DayOfWeek {
    val d = name.lowercase()
    return DayOfWeek.entries.first { it.name.lowercase().startsWith(d.take(3)) }
}

private fun findTime(text: String): LocalTime? {
    TIME_AMPM.find(text)?.let { m ->
        val hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (hour in 1..12 && minute in 0..59) {
            val pm = m.groupValues[3].equals("p", ignoreCase = true)
            return LocalTime.of(hour % 12 + if (pm) 12 else 0, minute)
        }
    }
    TIME_24H.find(text)?.let { m ->
        val hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].toInt()
        if (hour in 0..23 && minute in 0..59) return LocalTime.of(hour, minute)
    }
    TIME_WORD.find(text)?.let { m ->
        return if (m.groupValues[1].equals("noon", ignoreCase = true)) LocalTime.NOON else LocalTime.MIDNIGHT
    }
    TIME_AT_HOUR.find(text)?.let { m ->
        val hour = m.groupValues[1].toInt()
        if (hour in 0..23) return LocalTime.of(hour, 0)
    }
    return null
}

/** A calendar date ("Oct 12"), moved to next year if it has already passed this year. */
private fun calendarDate(month: Int, day: Int, year: String, today: LocalDate): LocalDate? {
    if (month < 1) return null
    val y = year.toIntOrNull() ?: today.year
    val date = runCatching { LocalDate.of(y, month, day) }.getOrNull() ?: return null
    return if (year.isEmpty() && date.isBefore(today)) date.plusYears(1) else date
}

/**
 * Finds a date/time in [text], relative to [now] (the phone's local time). Returns null when there
 * isn't one. Like the website, "today"/"tonight" on their own are chatter, not deadlines.
 */
fun findDate(text: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
    val today = now.toLocalDate()

    IN_SPAN.find(text)?.let { m ->
        val n = m.groupValues[1].let { if (it.equals("a", true) || it.equals("an", true)) 1L else it.toLong() }
        val unit = m.groupValues[2].lowercase()
        return when {
            unit.startsWith("min") -> now.plusMinutes(n).withSecond(0).withNano(0)
            unit.startsWith("h") -> now.plusHours(n).withSecond(0).withNano(0)
            unit.startsWith("d") -> today.plusDays(n).atTime(DEFAULT_TIME)
            unit.startsWith("w") -> today.plusWeeks(n).atTime(DEFAULT_TIME)
            unit.startsWith("mo") -> today.plusMonths(n).atTime(DEFAULT_TIME)
            else -> today.plusYears(n).atTime(DEFAULT_TIME)
        }
    }

    val time = findTime(text)

    NEXT_SPAN.find(text)?.let { m ->
        val date = when (m.groupValues[1].lowercase()) {
            "week" -> today.plusWeeks(1)
            "month" -> today.plusMonths(1)
            else -> today.plusYears(1)
        }
        return date.atTime(time ?: DEFAULT_TIME)
    }

    // An explicit calendar date wins over a weekday or "tomorrow".
    val calendar = MONTH_DAY.find(text)?.let { m ->
        calendarDate(monthNumber(m.groupValues[1]), m.groupValues[2].toInt(), m.groupValues[3], today)
    } ?: DAY_MONTH.find(text)?.let { m ->
        calendarDate(monthNumber(m.groupValues[2]), m.groupValues[1].toInt(), m.groupValues[3], today)
    }
    if (calendar != null) return calendar.atTime(time ?: DEFAULT_TIME)

    DAY_WORD.find(text)?.let { m ->
        when (m.groupValues[1].lowercase()) {
            "tomorrow" -> return today.plusDays(1).atTime(time ?: DEFAULT_TIME)
            "today" -> if (time != null) return today.atTime(time)
            // "tonight at 9" means 9 pm.
            else -> if (time != null) return today.atTime(if (time.hour < 12) time.plusHours(12) else time)
        }
        if (time == null) return null // a vague "today"/"tonight" isn't a date
    }

    WEEKDAY.findAll(text).firstOrNull { m ->
        val isShort = m.groupValues[2].length <= 5 && !m.groupValues[2].endsWith("day", ignoreCase = true)
        !isShort || m.groupValues[1].isNotEmpty() || time != null
    }?.let { m ->
        val target = dayOfWeek(m.groupValues[2])
        var date = today.with(TemporalAdjusters.nextOrSame(target))
        // The same weekday as today means today only if a later time was given ("saturday 8pm").
        if (date == today && (time == null || !time.isAfter(now.toLocalTime()))) date = date.plusWeeks(1)
        return date.atTime(time ?: DEFAULT_TIME)
    }

    // Just a time: the next time the clock shows it.
    if (time != null) {
        val candidate = today.atTime(time)
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }
    return null
}
