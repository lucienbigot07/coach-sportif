package fr.coachsportif.app.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import fr.coachsportif.app.domain.Fmt
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Une activité enregistrée par la montre (Garmin Connect → Health Connect). */
data class WatchSession(
    val label: String,
    val type: Int,
    val start: Instant,
    val durationS: Long,
    val distanceM: Double?,
    val avgHr: Long?,
    val maxHr: Long?,
    val ascentM: Double?,
    val source: String,
)

/**
 * Lecture SEULE dans Health Connect : l'appli n'y écrit jamais.
 * Garmin Connect y publie les activités (distance, FC, dénivelé, durée) si le partage est activé dans
 * Garmin Connect → Paramètres → Applications connectées → Health Connect.
 */
object HealthImport {
    val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ElevationGainedRecord::class),
    )

    fun available(ctx: Context): Boolean =
        HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(ctx: Context): Boolean =
        HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    suspend fun sessions(ctx: Context, date: LocalDate): List<WatchSession> {
        val client = HealthConnectClient.getOrCreate(ctx)
        val from = date.atStartOfDay(Fmt.PARIS).toInstant()
        val to = date.plusDays(1).atStartOfDay(Fmt.PARIS).toInstant()
        val records = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(from, to))).records
        return records.sortedBy { it.startTime }.map { r ->
            val agg = runCatching {
                client.aggregate(AggregateRequest(
                    metrics = setOf(DistanceRecord.DISTANCE_TOTAL, HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX,
                        ElevationGainedRecord.ELEVATION_GAINED_TOTAL, ExerciseSessionRecord.EXERCISE_DURATION_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(r.startTime, r.endTime),
                    dataOriginFilter = setOf(r.metadata.dataOrigin),
                ))
            }.getOrNull()
            val active = agg?.get(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)
            WatchSession(
                label = r.title?.takeIf { it.isNotBlank() } ?: typeLabel(r.exerciseType),
                type = r.exerciseType,
                start = r.startTime,
                durationS = (active ?: Duration.between(r.startTime, r.endTime)).seconds,
                distanceM = agg?.get(DistanceRecord.DISTANCE_TOTAL)?.inMeters?.takeIf { it > 0 },
                avgHr = agg?.get(HeartRateRecord.BPM_AVG),
                maxHr = agg?.get(HeartRateRecord.BPM_MAX),
                ascentM = agg?.get(ElevationGainedRecord.ELEVATION_GAINED_TOTAL)?.inMeters,
                source = r.metadata.dataOrigin.packageName,
            )
        }
    }

    fun typeLabel(t: Int): String = when (t) {
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Course à pied"
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Marche"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Randonnée"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Natation (piscine)"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Natation (eau libre)"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Vélo"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Musculation"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Football"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rameur"
        else -> "Activité"
    }

    fun sourceLabel(pkg: String) = when {
        pkg.contains("garmin") -> "Garmin"
        pkg.contains("samsung") || pkg.contains("shealth") -> "Samsung Health"
        pkg.contains("strava") -> "Strava"
        else -> pkg
    }
}
