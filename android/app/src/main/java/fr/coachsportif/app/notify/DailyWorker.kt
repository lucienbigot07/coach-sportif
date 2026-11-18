package fr.coachsportif.app.notify

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import fr.coachsportif.app.CoachApp
import fr.coachsportif.app.domain.Fmt
import fr.coachsportif.app.domain.bilan
import fr.coachsportif.app.domain.bilanMessage
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Tous les soirs vers 20 h 30 :
 * - rappel si une séance du jour n'est pas encore enregistrée (ni faite, ni « pas faite ») ;
 * - le dimanche : bilan de la semaine.
 */
class DailyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CoachApp
        val repo = app.repository
        if (repo.session.value == null || !RestTimer.canNotify(app)) return Result.success()
        val today = Fmt.today()
        val nm = NotificationManagerCompat.from(app)

        val day = runCatching { repo.refreshDay(today.toString()) }.getOrNull()
        val pending = day?.activities.orEmpty()
            .filter { it.status in listOf("planned", "in_progress") && it.sport != "football" }
        if (pending.isNotEmpty()) try {
            nm.notify(7101, NotificationCompat.Builder(app, RestTimer.CH_REMIND)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Séance du jour pas encore enregistrée")
                .setContentText(pending.joinToString(", ") { it.title })
                .setStyle(NotificationCompat.BigTextStyle().bigText(
                    pending.joinToString(", ") { it.title } + "\nSaisis-la dans l'appli, ou indique « Pas fait » pour que le coach adapte la suite."))
                .setAutoCancel(true).setContentIntent(RestTimer.openApp(app)).build())
        } catch (_: SecurityException) { }

        if (today.dayOfWeek == DayOfWeek.SUNDAY) {
            val week = repo.weekPlan(Fmt.mondayOf(today).toString())
            if (week != null) try {
                val b = week.bilan()
                nm.notify(7102, NotificationCompat.Builder(app, RestTimer.CH_REMIND)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("📊 Bilan de la semaine : ${b.done}/${b.planned} séances")
                    .setContentText(listOfNotNull(
                        b.runKm.takeIf { it > 0 }?.let { "${Fmt.num(it)} km de course" },
                        b.strengthSets.takeIf { it > 0 }?.let { "$it séries de muscu" },
                    ).joinToString(" · ").ifEmpty { "Ouvre l'onglet Semaine pour le détail" })
                    .setStyle(NotificationCompat.BigTextStyle().bigText(week.bilanMessage().lines().drop(1).dropLast(1).joinToString("\n")))
                    .setAutoCancel(true).setContentIntent(RestTimer.openApp(app)).build())
            } catch (_: SecurityException) { }
        }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            val now = ZonedDateTime.now(Fmt.PARIS)
            var next = now.with(LocalTime.of(20, 30))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("coach-daily", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
