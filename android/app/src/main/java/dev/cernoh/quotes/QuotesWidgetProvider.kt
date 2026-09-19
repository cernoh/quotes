package dev.cernoh.quotes

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

/**
 * The app widget. The system calls [onUpdate] when the widget appears or when
 * the launcher asks for a refresh; the alarm and the widget taps arrive as
 * broadcasts and land in [onReceive].
 */
class QuotesWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray,
    ) {
        widgetIds.forEach { WidgetRenderer.update(context, manager, it) }
        AlarmScheduler.schedule(context)
    }

    override fun onEnabled(context: Context) {
        AlarmScheduler.schedule(context)
    }

    /** The user resized the widget, so the card has to be measured again. */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        newOptions: android.os.Bundle?,
    ) {
        WidgetRenderer.update(context, manager, widgetId)
    }

    override fun onDisabled(context: Context) {
        AlarmScheduler.cancel(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Actions.NEXT, Actions.ROTATE -> {
                Prefs.advance(context)
                WidgetRenderer.updateAll(context)
            }

            else -> super.onReceive(context, intent)
        }
    }
}
