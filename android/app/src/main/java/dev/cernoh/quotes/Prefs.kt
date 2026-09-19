package dev.cernoh.quotes

import android.content.Context
import java.util.Collections
import java.util.Random

/**
 * What the widget needs to remember: where it is in the corpus, the order that
 * corpus was shuffled into, and every setting.
 *
 * The shuffle is regenerated from the stored seed instead of being stored, so a
 * widget update after a reboot shows the same order.
 */
object Prefs {
    private const val NAME = "dev.cernoh.quotes.prefs"

    /** Rotation value that means "only when the widget is tapped". */
    const val NEVER = 0
    const val DEFAULT_INTERVAL_MINUTES = 30
    const val MIN_INTERVAL_MINUTES = 15
    const val DEFAULT_TEXT_SCALE = 100
    const val MIN_TEXT_SCALE = 80
    const val MAX_TEXT_SCALE = 140

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    // Rotation ------------------------------------------------------------

    fun intervalMinutes(context: Context): Int =
        prefs(context).getInt("intervalMinutes", DEFAULT_INTERVAL_MINUTES)

    fun setIntervalMinutes(context: Context, minutes: Int) {
        val bounded = if (minutes <= NEVER) NEVER else minutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        prefs(context).edit().putInt("intervalMinutes", bounded).apply()
    }

    fun rotates(context: Context): Boolean = intervalMinutes(context) > NEVER

    // Sources -------------------------------------------------------------

    fun authors(context: Context): Set<String> =
        prefs(context).getStringSet("authors", emptySet()) ?: emptySet()

    fun works(context: Context): Set<String> =
        prefs(context).getStringSet("works", emptySet()) ?: emptySet()

    fun setAuthors(context: Context, authors: Set<String>) {
        prefs(context).edit().putStringSet("authors", authors).apply()
        restart(context)
    }

    fun setWorks(context: Context, works: Set<String>) {
        prefs(context).edit().putStringSet("works", works).apply()
        restart(context)
    }

    // Card ---------------------------------------------------------------

    /** Text scale in percent, applied on top of the size class of the widget. */
    fun textScale(context: Context): Int =
        prefs(context).getInt("textScale", DEFAULT_TEXT_SCALE)
            .coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)

    fun setTextScale(context: Context, percent: Int) {
        prefs(context).edit()
            .putInt("textScale", percent.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE))
            .apply()
    }

    fun showWork(context: Context): Boolean = prefs(context).getBoolean("showWork", true)

    fun setShowWork(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean("showWork", show).apply()
    }

    fun lightCard(context: Context): Boolean = prefs(context).getBoolean("lightCard", false)

    fun setLightCard(context: Context, light: Boolean) {
        prefs(context).edit().putBoolean("lightCard", light).apply()
    }

    // Order ---------------------------------------------------------------

    fun shuffle(context: Context): Boolean = prefs(context).getBoolean("shuffle", true)

    fun setShuffle(context: Context, shuffle: Boolean) {
        prefs(context).edit().putBoolean("shuffle", shuffle).apply()
        restart(context)
    }

    // Updates -------------------------------------------------------------

    fun githubToken(context: Context): String = prefs(context).getString("githubToken", "") ?: ""

    fun setGithubToken(context: Context, token: String) {
        prefs(context).edit().putString("githubToken", token.trim()).apply()
    }

    // Installation ---------------------------------------------------------

    /** True when the user lets Shizuku install updates without a prompt. */
    fun shizukuInstall(context: Context): Boolean =
        prefs(context).getBoolean("shizukuInstall", false)

    fun setShizukuInstall(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean("shizukuInstall", on).apply()
    }

    // Position -------------------------------------------------------------

    /** Start the cycle again, with a fresh order. */
    fun restart(context: Context) {
        prefs(context).edit()
            .putInt("position", 0)
            .putLong("seed", System.nanoTime())
            .apply()
    }

    private fun position(context: Context): Int = prefs(context).getInt("position", 0)

    private fun seed(context: Context): Long = prefs(context).getLong("seed", 1L)

    private fun order(context: Context, size: Int): IntArray {
        val ordered = MutableList(size) { it }
        if (!shuffle(context)) return ordered.toIntArray()
        Collections.shuffle(ordered, Random(seed(context)))
        return ordered.toIntArray()
    }

    /** The quote the widget shows now. */
    fun current(context: Context): Quote {
        val quotes = Corpus.filtered(context)
        val order = order(context, quotes.size)
        return quotes[order[position(context) % order.size]]
    }

    /** Move to the next quote, and reshuffle once the cycle is spent. */
    fun advance(context: Context) {
        val size = Corpus.filtered(context).size
        val next = position(context) + 1
        if (next >= size) {
            restart(context)
        } else {
            prefs(context).edit().putInt("position", next).apply()
        }
    }
}
