package dev.cernoh.quotes

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * Shows the card the widget draws, and the two things a reader wants from it:
 * the next quote, and the widget on the home screen. Every setting lives one tap
 * away, on [SettingsActivity].
 */
class MainActivity : Activity() {
    private lateinit var quoteText: TextView
    private lateinit var authorText: TextView
    private lateinit var workText: TextView
    private lateinit var sourceText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WindowSpacing.apply(this, findViewById(R.id.root))

        quoteText = findViewById(R.id.quote_text)
        authorText = findViewById(R.id.quote_author)
        workText = findViewById(R.id.quote_work)
        sourceText = findViewById(R.id.source_text)

        findViewById<Button>(R.id.button_next).setOnClickListener {
            Prefs.advance(this)
            showQuote()
        }
        findViewById<Button>(R.id.button_add).setOnClickListener { pinWidget() }
        findViewById<Button>(R.id.button_settings).setOnClickListener {
            startActivity(SettingsActivity.intent(this))
        }
        sourceText.setOnClickListener { openSource() }
    }

    override fun onResume() {
        super.onResume()
        showQuote()
    }

    /** The card preview shows the card as the settings leave it. */
    private fun showQuote() {
        val quote = Prefs.current(this)
        quoteText.text = "\u201C${quote.text}\u201D"
        authorText.text = quote.author.uppercase()
        val work = if (Prefs.showWork(this)) quote.work else null
        workText.text = work ?: ""
        workText.visibility = if (work.isNullOrBlank()) View.GONE else View.VISIBLE
        sourceText.text = getString(R.string.source_label, quote.source)
        sourceText.tag = quote.source

        val light = Prefs.lightCard(this)
        findViewById<View>(R.id.quote_card).setBackgroundResource(
            if (light) R.drawable.card_background_light else R.drawable.card_background,
        )
        quoteText.setTextColor(getColor(if (light) R.color.quote_text_light else R.color.quote_text))
        authorText.setTextColor(
            getColor(if (light) R.color.quote_author_light else R.color.quote_author),
        )
        workText.setTextColor(getColor(if (light) R.color.quote_work_light else R.color.quote_work))
    }

    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this) ?: return
        val provider = ComponentName(this, QuotesWidgetProvider::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(provider, null, null)
        } else {
            Toast.makeText(this, R.string.pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    private fun openSource() {
        val source = sourceText.tag as? String ?: return
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(source)))
    }
}
