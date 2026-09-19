package dev.cernoh.quotes

import android.content.Context
import java.util.Collections
import java.util.Random

/**
 * What the widget needs to remember: where it is in the corpus, the order that
 * corpus was shuffled into, and the settings.
 *
 * The shuffle is regenerated from the stored seed instead of being stored, so a
 * widget update after a reboot shows the same order.
 */
object Prefs {
    private const val NAME = "dev.cernoh.quotes.prefs"

    const val DEFAULT_INTERVAL_MINUTES = 30
    const val MIN_INTERVAL_MINUTES = 15

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun intervalMinutes(context: Context): Int =
        prefs(context).getInt("intervalMinutes", DEFAULT_INTERVAL_MINUTES)

    fun setIntervalMinutes(context: Context, minutes: Int) {
        val bounded = minutes.coerceAtLeast(MIN_INTERVAL_MINUTES)
        prefs(context).edit().putInt("intervalMinutes", bounded).apply()
    }

    fun author(context: Context): String = prefs(context).getString("author", "") ?: ""

    fun work(context: Context): String = prefs(context).getString("work", "") ?: ""

    fun setFilters(context: Context, author: String, work: String) {
        prefs(context).edit()
            .putString("author", author.trim())
            .putString("work", work.trim())
            .apply()
        restart(context)
    }

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
        val shuffled = MutableList(size) { it }
        Collections.shuffle(shuffled, Random(seed(context)))
        return shuffled.toIntArray()
    }

    /** The quote the widget shows now. */
    fun current(context: Context): Quote {
        val quotes = Corpus.filtered(context, author(context), work(context))
        val order = order(context, quotes.size)
        return quotes[order[position(context) % order.size]]
    }

    /** Move to the next quote, and reshuffle once the cycle is spent. */
    fun advance(context: Context) {
        val size = Corpus.filtered(context, author(context), work(context)).size
        val next = position(context) + 1
        if (next >= size) {
            restart(context)
        } else {
            prefs(context).edit().putInt("position", next).apply()
        }
    }
}
