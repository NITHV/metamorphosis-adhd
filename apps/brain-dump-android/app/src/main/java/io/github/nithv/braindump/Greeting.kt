package io.github.nithv.braindump

/**
 * Same wording as the website's welcome card. Midnight to 5 AM gets a gentler line:
 * late-night spirals are prime dumping time.
 */
fun greeting(hour: Int): String = when {
    hour < 5 -> "Hey, night owl 🦉"
    hour < 12 -> "Good morning 👋"
    hour < 17 -> "Good afternoon 👋"
    else -> "Good evening 👋"
}
