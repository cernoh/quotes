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

    /** Every author name, sorted, for the settings list. */
    fun authors(context: Context): List<String> =
        load(context).map { it.author }.distinct().sorted()

    /** Every work title, sorted, for the settings list. */
    fun works(context: Context): List<String> =
        load(context).mapNotNull { it.work }.distinct().sorted()

    /**
     * Quotes matching the stored source selection. An empty selection matches
     * everything, and a selection that leaves nothing falls back to the whole
     * corpus, so the widget can never end up empty.
     */
    fun filtered(context: Context): List<Quote> =
        filtered(load(context), Prefs.authors(context), Prefs.works(context))

    /** The same rule, over a given corpus and selection. */
    fun filtered(all: List<Quote>, authors: Set<String>, works: Set<String>): List<Quote> {
        if (authors.isEmpty() && works.isEmpty()) return all
        val kept = all.filter { quote ->
            (authors.isEmpty() || authors.contains(quote.author)) &&
                (works.isEmpty() || works.contains(quote.work))
        }
        return kept.ifEmpty { all }
    }
}
