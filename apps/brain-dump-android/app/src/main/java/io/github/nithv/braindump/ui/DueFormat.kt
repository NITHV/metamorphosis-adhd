package io.github.nithv.braindump.ui

import io.github.nithv.braindump.sort.DEFAULT_TIME
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * "Today 3:00 PM", "Tomorrow", "Friday", "Fri 9 Oct, 10:00 AM" (same as the website's formatDue).
 * 9:00 is the "no time given" default, so it isn't shown.
 */
fun formatDue(due: LocalDateTime, now: LocalDateTime, locale: Locale = Locale.getDefault()): String {
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate())
    val time = if (due.toLocalTime() == DEFAULT_TIME) "" else DateTimeFormatter.ofPattern("h:mm a", locale).format(due)
    val day = when {
        days == 0L -> "Today"
        days == 1L -> "Tomorrow"
        days == -1L -> "Yesterday"
        days in 2..6 -> due.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        else -> DateTimeFormatter.ofPattern("EEE d MMM", locale).format(due)
    }
    return when {
        time.isEmpty() -> day
        days in -1..6 -> "$day $time"
        else -> "$day, $time"
    }
}
