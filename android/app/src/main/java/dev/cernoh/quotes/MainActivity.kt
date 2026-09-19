package dev.cernoh.quotes

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/**
 * Shows the same card the widget draws, and holds the settings: how often the
 * quote rotates, which authors or works to draw from, and the button that pins
 * the widget to the home screen.
 */
class MainActivity : Activity() {
    private val intervals = intArrayOf(15, 30, 60, 120, 240)

    private lateinit var quoteText: TextView
    private lateinit var authorText: TextView
    private lateinit var workText: TextView
    private lateinit var sourceText: TextView
    private lateinit var authorFilter: EditText
    private lateinit var workFilter: EditText
    private lateinit var intervalSpinner: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WindowSpacing.apply(this, findViewById(R.id.root))

        quoteText = findViewById(R.id.quote_text)
        authorText = findViewById(R.id.quote_author)
        workText = findViewById(R.id.quote_work)
        sourceText = findViewById(R.id.source_text)
        authorFilter = findViewById(R.id.filter_author)
        workFilter = findViewById(R.id.filter_work)
        intervalSpinner = findViewById(R.id.interval_spinner)

        intervalSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            intervals.map { intervalLabel(it) },
        )
        intervalSpinner.setSelection(
            intervals.indexOf(Prefs.intervalMinutes(this)).coerceAtLeast(0),
        )

        authorFilter.setText(Prefs.author(this))
        workFilter.setText(Prefs.work(this))

        findViewById<Button>(R.id.button_next).setOnClickListener {
            Prefs.advance(this)
            showQuote()
        }
        findViewById<Button>(R.id.button_add).setOnClickListener { pinWidget() }
        findViewById<Button>(R.id.button_apply).setOnClickListener { applySettings() }
        sourceText.setOnClickListener { openSource() }

        showQuote()
    }

    private fun intervalLabel(minutes: Int): String = when {
        minutes < 60 -> getString(R.string.interval_minutes, minutes)
        minutes == 60 -> getString(R.string.interval_hour)
        else -> getString(R.string.interval_hours, minutes / 60)
    }

    private fun showQuote() {
        val quote = Prefs.current(this)
        quoteText.text = "\u201C${quote.text}\u201D"
        authorText.text = quote.author.uppercase()
        val work = quote.work
        workText.text = work ?: ""
        workText.visibility = if (work.isNullOrBlank()) View.GONE else View.VISIBLE
        sourceText.text = getString(R.string.source_label, quote.source)
        sourceText.tag = quote.source
    }

    private fun applySettings() {
        Prefs.setIntervalMinutes(this, intervals[intervalSpinner.selectedItemPosition])
        Prefs.setFilters(this, authorFilter.text.toString(), workFilter.text.toString())
        AlarmScheduler.schedule(this)
        WidgetRenderer.updateAll(this)
        showQuote()
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
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
