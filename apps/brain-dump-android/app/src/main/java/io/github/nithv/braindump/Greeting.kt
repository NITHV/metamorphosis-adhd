package io.github.nithv.braindump

/** Same wording as the website's welcome card. */
fun greeting(hour: Int): String = when {
    hour < 12 -> "Good morning"
    hour < 17 -> "Good afternoon"
    else -> "Good evening"
}
