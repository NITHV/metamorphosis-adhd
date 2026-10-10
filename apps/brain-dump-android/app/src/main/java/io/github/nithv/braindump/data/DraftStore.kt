package io.github.nithv.braindump.data

import android.content.Context

/**
 * The half-typed text in the Dump box, kept on disk.
 *
 * Android's "saved instance state" only survives the system killing the app in the background;
 * it is thrown away when you swipe the app away or force-stop it. A half-written thought is
 * exactly what Brain Dump must not lose, so the draft is written to disk as you type.
 */
class DraftStore(context: Context) {
    private val prefs = context.getSharedPreferences("draft", Context.MODE_PRIVATE)

    fun load(): String = prefs.getString(KEY, "").orEmpty()

    /** apply() writes in the background and merges rapid keystrokes, so typing never waits on disk. */
    fun save(text: String) {
        prefs.edit().putString(KEY, text).apply()
    }

    private companion object {
        const val KEY = "dump_box"
    }
}
