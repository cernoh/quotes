package dev.cernoh.quotes

import android.content.Context
import org.json.JSONObject

/** One line from a book, with the page it was taken from. */
data class Quote(
    val author: String,
    val work: String?,
    val text: String,
    val source: String,
)

/**
 * The corpus, read from the asset that the build copies out of
 * `data/quotes.json`. The file is small, so it is parsed once and kept.
 */
object Corpus {
    private const val ASSET = "quotes.json"
    private var cached: List<Quote>? = null

    fun load(context: Context): List<Quote> {
        cached?.let { return it }
        val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val array = JSONObject(text).getJSONArray("quotes")
        val quotes = ArrayList<Quote>(array.length())
        for (index in 0 until array.length()) {
            val entry = array.getJSONObject(index)
            quotes += Quote(
                author = entry.getString("author"),
                work = if (entry.isNull("work")) null else entry.getString("work"),
                text = entry.getString("text"),
                source = entry.getString("source"),
            )
        }
        cached = quotes
        return quotes
    }

    /**
     * Quotes matching the filters. A blank filter matches everything, and a
     * filter that matches nothing falls back to the whole corpus, so the widget
     * can never end up empty.
     */
    fun filtered(context: Context, author: String, work: String): List<Quote> {
        val all = load(context)
        if (author.isBlank() && work.isBlank()) return all
        val kept = all.filter { quote ->
            (author.isBlank() || quote.author.contains(author, ignoreCase = true)) &&
                (work.isBlank() || (quote.work ?: "").contains(work, ignoreCase = true))
        }
        return kept.ifEmpty { all }
    }
}
