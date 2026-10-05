package com.mohamed.safi.fitness

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.mohamed.safi.data.zone
import java.time.Instant
import java.time.LocalDate

data class DayHealth(
    val date: LocalDate,
    val steps: Long = 0,
    val activeKcal: Double = 0.0,
    val restingHr: Long? = null,
    val avgHr: Long? = null,
    val maxHr: Long? = null,
    val sleepMin: Long = 0,
    val workouts: List<String> = emptyList(),
)

/**
 * Reads Galaxy Watch / Samsung Health data through Health Connect.
 * Samsung Health → Settings → Health Connect must be enabled on the phone.
 */
object Health {
    const val HC_PACKAGE = "com.google.android.apps.healthdata"

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.BloodPressureRecord::class),
        HealthPermission.getReadPermission(androidx.health.connect.client.records.OxygenSaturationRecord::class),
    )

    fun status(ctx: Context): Int = HealthConnectClient.getSdkStatus(ctx, HC_PACKAGE)
    fun available(ctx: Context) = status(ctx) == HealthConnectClient.SDK_AVAILABLE

    fun client(ctx: Context) = HealthConnectClient.getOrCreate(ctx)

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun granted(ctx: Context): Set<String> =
        if (!available(ctx)) emptySet() else runCatching { client(ctx).permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    suspend fun hasAny(ctx: Context) = granted(ctx).isNotEmpty()

    fun installIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$HC_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"))

    /** Health Connect needs Android 9 (API 28) or newer. */
    val supported get() = android.os.Build.VERSION.SDK_INT >= 28

    /** Opens Health Connect's permission screen for this app (used after the dialog is denied twice). */
    fun manageIntent(ctx: Context): Intent =
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, ctx.packageName)
        } else {
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        }

    private fun range(d: LocalDate): TimeRangeFilter {
        val s = d.atStartOfDay(zone).toInstant()
        val e = d.plusDays(1).atStartOfDay(zone).toInstant()
        return TimeRangeFilter.between(s, minOf(e, Instant.now()))
    }

    suspend fun day(ctx: Context, d: LocalDate): DayHealth {
        if (!available(ctx)) return DayHealth(d)
        val c = client(ctx)
        val g = granted(ctx)
        val r = range(d)
        var out = DayHealth(d)
        runCatching {
            val metrics = buildSet {
                if (HealthPermission.getReadPermission(StepsRecord::class) in g) add(StepsRecord.COUNT_TOTAL)
                if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in g) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                if (HealthPermission.getReadPermission(HeartRateRecord::class) in g) {
                    add(HeartRateRecord.BPM_AVG); add(HeartRateRecord.BPM_MAX)
                }
                if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in g) add(RestingHeartRateRecord.BPM_AVG)
            }
            if (metrics.isNotEmpty()) {
                val a = c.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = r))
                out = out.copy(
                    steps = a[StepsRecord.COUNT_TOTAL] ?: 0,
                    activeKcal = a[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0,
                    avgHr = a[HeartRateRecord.BPM_AVG],
                    maxHr = a[HeartRateRecord.BPM_MAX],
                    restingHr = a[RestingHeartRateRecord.BPM_AVG],
                )
            }
        }
        runCatching {
            if (HealthPermission.getReadPermission(SleepSessionRecord::class) in g) {
                // sleep that ended this day (started the evening before)
                val s = d.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
                val e = d.atTime(18, 0).atZone(zone).toInstant()
                val recs = c.readRecords(ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(s, e))).records
                out = out.copy(sleepMin = recs.sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() })
            }
        }
        runCatching {
            if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in g) {
                val recs = c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, r)).records
                out = out.copy(
                    workouts = recs.map {
                        val min = java.time.Duration.between(it.startTime, it.endTime).toMinutes()
                        (it.title?.takeIf { t -> t.isNotBlank() } ?: exerciseName(it.exerciseType)) + " • $min د"
                    },
                )
            }
        }
        return out
    }

    /** Weights from the watch / scale in the last [days] days. */
    suspend fun weights(ctx: Context, days: Long = 120): List<Pair<Long, Double>> {
        if (!available(ctx)) return emptyList()
        if (HealthPermission.getReadPermission(WeightRecord::class) !in granted(ctx)) return emptyList()
        return runCatching {
            client(ctx).readRecords(
                ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(Instant.now().minusSeconds(days * 86_400))),
            ).records.map { it.time.toEpochMilli() to it.weight.inKilograms }
        }.getOrDefault(emptyList())
    }

    /** Copies new watch/scale weights into Safi's weight log. */
    suspend fun syncWeights(ctx: Context): Int {
        var n = 0
        for ((t, kg) in weights(ctx)) {
            if (Fit.dao.watchWeightExists(t) == 0) {
                Fit.dao.insertWeight(WeightEntry(time = t, kg = Math.round(kg * 10) / 10.0, source = "watch"))
                n++
            }
        }
        return n
    }

    private fun exerciseName(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "مشي"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "جري"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "جري على المشاية"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "عجلة"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "حديد"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "سباحة"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "إليبتيكال"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING, ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "سلالم"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "يوجا"
        else -> "تمرين"
    }
}
