package com.mohamed.safi.faith

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.zone
import com.mohamed.safi.location.LocationService
import com.mohamed.safi.notify.Notifier
import com.mohamed.safi.notify.ReminderScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.*

data class PrayerDay(val date: LocalDate, val times: List<Pair<String, LocalDateTime>>) {
    fun next(now: LocalDateTime = LocalDateTime.now(zone)) = times.filter { it.first != SUNRISE }.firstOrNull { it.second.isAfter(now) }

    companion object { const val SUNRISE = "الشروق" }
}

/**
 * Prayer times computed on the phone (PrayTimes.org astronomy).
 * UAE method: Fajr & Isha at 18.2°, Asr Shafi'i, with the small UAE adjustments (+3 min Dhuhr/Asr/Maghrib, −3 min sunrise).
 */
object Prayer {
    val names = listOf("الفجر", PrayerDay.SUNRISE, "الظهر", "العصر", "المغرب", "العشاء")

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_prayer", Context.MODE_PRIVATE)
    var lat: Double get() = sp().getString("lat", "25.2048")!!.toDouble(); set(v) = sp().edit { putString("lat", v.toString()) }
    var lng: Double get() = sp().getString("lng", "55.2708")!!.toDouble(); set(v) = sp().edit { putString("lng", v.toString()) }
    var city: String get() = sp().getString("city", "دبي") ?: "دبي"; set(v) = sp().edit { putString("city", v) }
    var alertsOn: Boolean get() = sp().getBoolean("alerts", false); set(v) = sp().edit { putBoolean("alerts", v) }
    var enabledPrayers: Set<String>
        get() = sp().getStringSet("enabled", names.filter { it != PrayerDay.SUNRISE }.toSet()) ?: emptySet()
        set(v) = sp().edit { putStringSet("enabled", v) }
    var preMinutes: Int get() = sp().getInt("pre", 0); set(v) = sp().edit { putInt("pre", v) }

    /** Uses the phone's last known location when available. */
    fun refreshLocation(ctx: Context): Boolean {
        val l = LocationService.currentLocation(ctx) ?: return false
        lat = l.latitude; lng = l.longitude
        city = com.mohamed.safi.location.LocationLogger.geocode(ctx, l.latitude, l.longitude).split("،").lastOrNull()?.trim()?.ifBlank { null } ?: city
        return true
    }

    // ---------- astronomy ----------
    private fun dsin(d: Double) = sin(Math.toRadians(d))
    private fun dcos(d: Double) = cos(Math.toRadians(d))
    private fun dtan(d: Double) = tan(Math.toRadians(d))
    private fun darcsin(x: Double) = Math.toDegrees(asin(x))
    private fun darccos(x: Double) = Math.toDegrees(acos(x.coerceIn(-1.0, 1.0)))
    private fun darctan2(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun darccot(x: Double) = Math.toDegrees(atan(1 / x))
    private fun fixAngle(a: Double) = a - 360.0 * floor(a / 360.0)
    private fun fixHour(a: Double) = a - 24.0 * floor(a / 24.0)

    private fun julian(y: Int, m: Int, d: Int): Double {
        var yy = y; var mm = m
        if (mm <= 2) { yy -= 1; mm += 12 }
        val a = floor(yy / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (yy + 4716)) + floor(30.6001 * (mm + 1)) + d + b - 1524.5
    }

    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val dd = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * dd)
        val q = fixAngle(280.459 + 0.98564736 * dd)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * dd
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return decl to eqt
    }

    fun compute(date: LocalDate, latitude: Double = lat, longitude: Double = lng): PrayerDay {
        val tz = ZonedDateTime.of(date.atTime(12, 0), zone).offset.totalSeconds / 3600.0
        val jDate = julian(date.year, date.monthValue, date.dayOfMonth) - longitude / (15.0 * 24.0)

        fun midDay(t: Double): Double = fixHour(12 - sunPosition(jDate + t).second)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPosition(jDate + t).first
            val noon = midDay(t)
            val x = darccos((-dsin(angle) - dsin(decl) * dsin(latitude)) / (dcos(decl) * dcos(latitude))) / 15.0
            return noon + if (ccw) -x else x
        }
        fun asr(factor: Double, t: Double): Double {
            val decl = sunPosition(jDate + t).first
            val angle = -darccot(factor + dtan(abs(latitude - decl)))
            return angleTime(angle, t, false)
        }

        // two iterations starting from rough guesses (hours / 24)
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0)
        repeat(2) {
            val p = t.map { it / 24.0 }
            t = doubleArrayOf(
                angleTime(18.2, p[0], true),
                angleTime(0.833, p[1], true),
                midDay(p[2]),
                asr(1.0, p[3]),
                angleTime(0.833, p[4], false),
                angleTime(18.2, p[5], false),
            )
        }
        val adjustMin = intArrayOf(0, -3, 3, 3, 3, 0)
        val times = t.mapIndexed { i, h ->
            val local = h + tz - longitude / 15.0
            val totalMin = (local * 60.0).roundToLong() + adjustMin[i]
            val dayMin = ((totalMin % 1440) + 1440) % 1440
            names[i] to date.atTime(LocalTime.of((dayMin / 60).toInt(), (dayMin % 60).toInt()))
        }
        return PrayerDay(date, times)
    }

    fun today() = compute(LocalDate.now(zone))

    /** Next prayer from now, looking into tomorrow if needed. */
    fun nextPrayer(): Pair<String, LocalDateTime> {
        val now = LocalDateTime.now(zone)
        return today().next(now) ?: compute(LocalDate.now(zone).plusDays(1)).times.first()
    }

    // ---------- Kaaba ----------
    private const val KAABA_LAT = 21.422487
    private const val KAABA_LNG = 39.826206

    fun qiblaBearing(latitude: Double = lat, longitude: Double = lng): Double {
        val p1 = Math.toRadians(latitude)
        val p2 = Math.toRadians(KAABA_LAT)
        val dl = Math.toRadians(KAABA_LNG - longitude)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun distanceToKaabaKm(latitude: Double = lat, longitude: Double = lng): Int {
        val r = FloatArray(1)
        android.location.Location.distanceBetween(latitude, longitude, KAABA_LAT, KAABA_LNG, r)
        return (r[0] / 1000).roundToInt()
    }

    // ---------- alerts ----------
    private fun pending(ctx: Context, name: String = ""): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 8_800_001, Intent(ctx, PrayerReceiver::class.java).putExtra("name", name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(ctx))
        if (!alertsOn) return
        val now = LocalDateTime.now(zone)
        val upcoming = (0..2).flatMap { compute(LocalDate.now(zone).plusDays(it.toLong())).times }
            .filter { it.first in enabledPrayers }
            .map { it.first to it.second.minusMinutes(preMinutes.toLong()) }
            .firstOrNull { it.second.isAfter(now) } ?: return
        val at = upcoming.second.atZone(zone).toInstant().toEpochMilli()
        val pi = pending(ctx, upcoming.first)
        try {
            if (ReminderScheduler.canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class PrayerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra("name") ?: ""
        if (name.isNotBlank() && Prayer.alertsOn) {
            val pre = Prayer.preMinutes
            Notifier.show(
                context, 8801, Notifier.CH_PRAYER,
                if (pre > 0) "🕌 صلاة $name بعد $pre دقيقة" else "🕌 حان الآن موعد صلاة $name",
                Prayer.city.let { "حسب توقيت $it" }, route = "prayer",
            )
        }
        Prayer.schedule(context)
        runCatching { com.mohamed.safi.widget.SafiWidget.updateAll(context) }
    }
}
