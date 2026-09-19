package dev.cernoh.quotes

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** The timer that rotates the quote without the widget being on screen. */
object AlarmScheduler {
    private const val REQUEST = 41

    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pending(context)
        alarms.cancel(pending)
        if (!Prefs.rotates(context)) return
        val interval = Prefs.intervalMinutes(context)
            .coerceAtLeast(Prefs.MIN_INTERVAL_MINUTES) * 60_000L
        alarms.setInexactRepeating(
            AlarmManager.RTC,
            System.currentTimeMillis() + interval,
            interval,
            pending,
        )
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending(context))
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, QuotesWidgetProvider::class.java).setAction(Actions.ROTATE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
