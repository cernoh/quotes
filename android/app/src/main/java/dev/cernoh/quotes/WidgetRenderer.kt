package dev.cernoh.quotes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

/** Draws the card into every placed widget. */
object WidgetRenderer {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, QuotesWidgetProvider::class.java))
        ids.forEach { update(context, manager, it) }
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val quote = Prefs.current(context)
        val views = RemoteViews(context.packageName, R.layout.widget_card)

        views.setTextViewText(R.id.quote_text, "\u201C${quote.text}\u201D")
        views.setTextViewText(R.id.quote_author, quote.author.uppercase())

        val work = quote.work
        if (work.isNullOrBlank()) {
            views.setViewVisibility(R.id.quote_work, View.GONE)
        } else {
            views.setTextViewText(R.id.quote_work, work)
            views.setViewVisibility(R.id.quote_work, View.VISIBLE)
        }

        // A tap anywhere on the quote moves on; the attribution opens the app.
        val next = nextIntent(context)
        views.setOnClickPendingIntent(R.id.quote_text, next)
        views.setOnClickPendingIntent(R.id.quote_rule, next)
        views.setOnClickPendingIntent(R.id.quote_card, next)
        views.setOnClickPendingIntent(R.id.quote_attribution, openIntent(context))

        manager.updateAppWidget(widgetId, views)
    }

    private fun nextIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        42,
        Intent(context, QuotesWidgetProvider::class.java).setAction(Actions.NEXT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        43,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
