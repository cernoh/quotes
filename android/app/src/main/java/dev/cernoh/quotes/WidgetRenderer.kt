package dev.cernoh.quotes

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews

/** Draws the card into every placed widget, at the size the launcher gave it. */
object WidgetRenderer {
    private const val TAG = "quotes"
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(context, QuotesWidgetProvider::class.java))
        ids.forEach { update(context, manager, it) }
    }

    fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val quote = Prefs.current(context)
        val views = RemoteViews(context.packageName, R.layout.widget_card)

        applyTier(context, views, manager, widgetId)

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

    /**
     * Size the card to the cell footprint. A 3 by 2 placement gets less text at
     * a smaller size, or the quote alone fills the card and the attribution
     * disappears.
     */
    private fun applyTier(
        context: Context,
        views: RemoteViews,
        manager: AppWidgetManager,
        widgetId: Int,
    ) {
        val options = manager.getAppWidgetOptions(widgetId)
        val portrait = context.resources.configuration.orientation ==
            Configuration.ORIENTATION_PORTRAIT
        val (widthDp, heightDp) = WidgetSizing.currentSize(
            portrait = portrait,
            minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT),
            maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
            maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
        )
        val tier = WidgetSizing.tierFor(widthDp, heightDp)
        Log.i(
            TAG,
            "widget $widgetId is ${widthDp}x${heightDp}dp: ${tier.quoteSizeSp}sp, " +
                "maxLines ${tier.maxLines}, padding ${tier.paddingDp}dp",
        )

        val scale = Prefs.textScale(context) / 100f
        views.setTextViewTextSize(
            R.id.quote_text, TypedValue.COMPLEX_UNIT_SP, tier.quoteSizeSp * scale,
        )
        views.setTextViewTextSize(
            R.id.quote_author, TypedValue.COMPLEX_UNIT_SP, tier.attributionSizeSp * scale,
        )
        views.setTextViewTextSize(
            R.id.quote_work, TypedValue.COMPLEX_UNIT_SP, tier.attributionSizeSp * scale,
        )
        views.setInt(R.id.quote_text, "setMaxLines", tier.maxLines)

        val padding = (tier.paddingDp * context.resources.displayMetrics.density).toInt()
        views.setViewPadding(R.id.quote_card, padding, padding, padding, padding)

        views.setViewVisibility(
            R.id.quote_rule, if (tier.showRule) View.VISIBLE else View.GONE,
        )

        // The card settings come on top of what the size allows.
        val light = Prefs.lightCard(context)
        views.setInt(
            R.id.quote_card, "setBackgroundResource",
            if (light) R.drawable.card_background_light else R.drawable.card_background,
        )
        views.setTextColor(
            R.id.quote_text, context.getColor(if (light) R.color.quote_text_light else R.color.quote_text),
        )
        views.setTextColor(
            R.id.quote_author,
            context.getColor(if (light) R.color.quote_author_light else R.color.quote_author),
        )
        views.setTextColor(
            R.id.quote_work,
            context.getColor(if (light) R.color.quote_work_light else R.color.quote_work),
        )
        views.setInt(
            R.id.quote_rule, "setBackgroundColor",
            context.getColor(if (light) R.color.quote_rule_light else R.color.quote_rule),
        )

        if (!tier.showWork || !Prefs.showWork(context)) {
            views.setViewVisibility(R.id.quote_work, View.GONE)
        }
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
