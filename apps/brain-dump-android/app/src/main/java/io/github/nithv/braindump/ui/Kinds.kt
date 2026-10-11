package io.github.nithv.braindump.ui

import androidx.compose.ui.graphics.Color
import io.github.nithv.braindump.data.Kind
import io.github.nithv.braindump.ui.theme.BrainDumpColors

/** How each pile looks and reads, same as the website (apps/brain-dump/src/lib/kinds.ts). */
data class KindLook(val label: String, val pile: String, val hint: String, val empty: String)

val Kind.look: KindLook
    get() = when (this) {
        Kind.TASK -> KindLook("Task", "Tasks", "Things to do", "No tasks. File some from your Inbox, or enjoy the quiet.")
        Kind.IDEA -> KindLook("Idea", "Ideas", "Sparks to keep", "No ideas parked yet. They'll land here when you file them.")
        Kind.REMINDER -> KindLook("Reminder", "Reminders", "Has a date", "Nothing to remember right now.")
        Kind.WORRY -> KindLook("Worry", "Worries", "Parked, not overdue", "No worries parked. Nice.")
    }

fun Kind.color(c: BrainDumpColors): Color = when (this) {
    Kind.TASK -> c.blue
    Kind.IDEA -> c.orange
    Kind.REMINDER -> c.purple
    Kind.WORRY -> c.brand
}

fun Kind.soft(c: BrainDumpColors): Color = when (this) {
    Kind.TASK -> c.blueSoft
    Kind.IDEA -> c.orangeSoft
    Kind.REMINDER -> c.purpleSoft
    Kind.WORRY -> c.brandSoft
}
