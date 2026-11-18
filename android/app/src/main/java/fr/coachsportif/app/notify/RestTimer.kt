package fr.coachsportif.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.coachsportif.app.MainActivity

/**
 * Minuteur de repos entre deux séries.
 * - pendant le repos : notification silencieuse avec compte à rebours (visible écran verrouillé) ;
 * - à la fin : notification qui vibre. Garmin Connect la relaie sur la montre → la montre vibre aussi.
 * L'alarme passe par AlarmManager : elle sonne même si l'appli est en arrière-plan.
 */
object RestTimer {
    const val CH_LIVE = "rest_live"
    const val CH_DONE = "rest_done"
    const val CH_REMIND = "reminders"
    private const val ID_LIVE = 7001
    const val ID_DONE = 7002
    private const val PREFS = "coach_prefs"
    private const val KEY_REST = "rest_seconds"

    fun channels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_LIVE, "Repos en cours", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Compte à rebours entre deux séries"; setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_DONE, "Fin du repos", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Vibre (téléphone et montre) quand il est temps de reprendre"
            enableVibration(true); vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
        })
        nm.createNotificationChannel(NotificationChannel(CH_REMIND, "Rappels et bilans", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Séance du jour non enregistrée, bilan de la semaine"
        })
    }

    fun defaultSeconds(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_REST, 90)
    fun setDefaultSeconds(ctx: Context, s: Int) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(KEY_REST, s).apply()

    fun canNotify(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(ctx, 0,
        Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun alarmIntent(ctx: Context, label: String? = null) = PendingIntent.getBroadcast(ctx, 4711,
        Intent(ctx, RestDoneReceiver::class.java).apply { label?.let { putExtra("label", it) } },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Démarre (ou redémarre) le repos jusqu'à [endAtMs]. [label] = exercice suivant / en cours. */
    fun start(ctx: Context, endAtMs: Long, label: String) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.cancel(ID_DONE)
        if (canNotify(ctx)) try {
            nm.notify(ID_LIVE, NotificationCompat.Builder(ctx, CH_LIVE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Repos — $label")
                .setContentText("Reprise à la fin du compte à rebours")
                .setWhen(endAtMs).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setContentIntent(openApp(ctx))
                .setTimeoutAfter((endAtMs - System.currentTimeMillis()).coerceAtLeast(1000))
                .build())
        } catch (_: SecurityException) { }
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx, label)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMs, pi)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMs, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMs, pi)
        }
    }

    fun cancel(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(alarmIntent(ctx))
        NotificationManagerCompat.from(ctx).apply { cancel(ID_LIVE); cancel(ID_DONE) }
    }

    fun clearDone(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID_DONE)
}

class RestDoneReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.cancel(7001)
        if (!RestTimer.canNotify(ctx)) return
        val label = intent.getStringExtra("label")
        try {
            nm.notify(RestTimer.ID_DONE, NotificationCompat.Builder(ctx, RestTimer.CH_DONE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏱ Repos terminé")
                .setContentText(label?.let { "Série suivante : $it" } ?: "Série suivante")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(longArrayOf(0, 400, 200, 400, 200, 600))
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .setContentIntent(RestTimer.openApp(ctx))
                .build())
        } catch (_: SecurityException) { }
    }
}
