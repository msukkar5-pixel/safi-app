package com.mohamed.safi.ui.screens

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.data.zone
import com.mohamed.safi.faith.Prayer
import com.mohamed.safi.faith.PrayerDay
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun t12(t: LocalDateTime) = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US)).replace("AM", "ص").replace("PM", "م")

@Composable
fun PrayerCard(open: (String) -> Unit) {
    var next by remember { mutableStateOf(Prayer.nextPrayer()) }
    var now by remember { mutableStateOf(LocalDateTime.now(zone)) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now(zone); next = Prayer.nextPrayer() } }
    val left = Duration.between(now, next.second)
    AppCard(onClick = { open("prayer") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatBadge("prayer", 40, Icons.Default.Mosque, Brand)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${next.first} ${t12(next.second)}", fontWeight = FontWeight.Bold)
                Text("باقي ${left.toHours()} ساعة و ${(left.toMinutes() % 60)} دقيقة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun PrayerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var day by remember { mutableStateOf(Prayer.today()) }
    var now by remember { mutableStateOf(LocalDateTime.now(zone)) }
    var alerts by remember { mutableStateOf(Prayer.alertsOn) }
    var enabled by remember { mutableStateOf(Prayer.enabledPrayers) }
    var pre by remember { mutableIntStateOf(Prayer.preMinutes) }
    var city by remember { mutableStateOf(Prayer.city) }

    LaunchedEffect(Unit) {
        if (Prayer.refreshLocation(ctx)) { day = Prayer.today(); city = Prayer.city; Prayer.schedule(ctx) }
        while (true) { delay(1000); now = LocalDateTime.now(zone); if (now.toLocalDate() != day.date) day = Prayer.today() }
    }
    val next = day.next(now) ?: Prayer.nextPrayer()

    ScreenScaffold("الصلاة والقبلة", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                AppCard(color = MaterialTheme.colorScheme.primary) {
                    val onP = MaterialTheme.colorScheme.onPrimary
                    val left = Duration.between(now, next.second)
                    Text("الصلاة الجاية", color = onP.copy(alpha = 0.8f))
                    Text("${next.first} • ${t12(next.second)}", color = onP, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "%d:%02d:%02d".format(left.toHours(), (left.toMinutes() % 60), (left.seconds % 60)),
                        color = onP, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    )
                    Text("حسب $city • طريقة الإمارات", color = onP.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                AppCard {
                    day.times.forEach { (n, t) ->
                        val isNext = n == next.first && t == next.second
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(n, Modifier.weight(1f), fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal, color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (alerts && n != PrayerDay.SUNRISE) {
                                Checkbox(n in enabled, { on ->
                                    enabled = if (on) enabled + n else enabled - n
                                    Prayer.enabledPrayers = enabled; Prayer.schedule(ctx)
                                })
                            }
                            Text(t12(t), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("تنبيه الصلاة", fontWeight = FontWeight.SemiBold)
                            Text("إشعار في وقت كل صلاة (علّم على اللي عايزها)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(alerts, { alerts = it; Prayer.alertsOn = it; Prayer.schedule(ctx) })
                    }
                    if (alerts) ChoiceField("نبهني", pre.toString(), listOf("0", "5", "10", "15", "20"), display = { if (it == "0") "في وقت الأذان" else "قبلها بـ $it دقيقة" }) {
                        pre = it.toInt(); Prayer.preMinutes = pre; Prayer.schedule(ctx)
                    }
                }
            }
            item { SectionTitle("القبلة") }
            item { QiblaCompass() }
            item {
                Text(
                    "المواعيد محسوبة على تليفونك حسب مكانك (الفجر والعشاء 18.2°)، وممكن تفرق دقيقة عن تقويم الأوقاف.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
fun QiblaCompass() {
    val ctx = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }
    var accuracyLow by remember { mutableStateOf(false) }
    val qibla = remember { Prayer.qiblaBearing().toFloat() }
    val declination = remember {
        GeomagneticField(Prayer.lat.toFloat(), Prayer.lng.toFloat(), 0f, System.currentTimeMillis()).declination
    }
    val sm = remember { ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val sensor = remember { sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }

    DisposableEffect(sensor) {
        val rot = FloatArray(9)
        val ori = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val deg = (Math.toDegrees(ori[0].toDouble()).toFloat() + declination + 360f) % 360f
                // smooth
                val diff = ((deg - azimuth + 540f) % 360f) - 180f
                azimuth = (azimuth + diff * 0.2f + 360f) % 360f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) { accuracyLow = a < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM }
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }

    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val gold = Gold
    val turn = ((qibla - azimuth + 540f) % 360f) - 180f
    val aligned = kotlin.math.abs(turn) < 4f

    AppCard {
        if (sensor == null) {
            Text("تليفونك مفيهوش بوصلة. القبلة ${qibla.toInt()}° من الشمال.")
            return@AppCard
        }
        Box(Modifier.fillMaxWidth().aspectRatio(1f).padding(12.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(outline, r, c, style = Stroke(6f))
                rotate(-azimuth, c) {
                    // north tick
                    drawLine(androidx.compose.ui.graphics.Color.Red, Offset(c.x, c.y - r), Offset(c.x, c.y - r + 40f), strokeWidth = 8f)
                    for (i in 0 until 36) {
                        rotate(i * 10f, c) { drawLine(outline, Offset(c.x, c.y - r), Offset(c.x, c.y - r + if (i % 9 == 0) 28f else 14f), strokeWidth = 3f) }
                    }
                    rotate(qibla, c) {
                        val p = Path().apply {
                            moveTo(c.x, c.y - r * 0.85f)
                            lineTo(c.x - r * 0.12f, c.y - r * 0.55f)
                            lineTo(c.x + r * 0.12f, c.y - r * 0.55f)
                            close()
                        }
                        drawLine(if (aligned) gold else primary, c, Offset(c.x, c.y - r * 0.6f), strokeWidth = 12f)
                        drawPath(p, if (aligned) gold else primary)
                        drawCircle(if (aligned) gold else primary, r * 0.07f, Offset(c.x, c.y - r * 0.92f))
                    }
                }
                drawCircle(primary, 10f, c)
                // phone heading marker
                drawLine(primary.copy(alpha = 0.5f), Offset(c.x, c.y - r - 4f), Offset(c.x, c.y - r + 24f), strokeWidth = 6f)
            }
        }
        Text(
            if (aligned) "✓ انت متجه للقبلة" else if (turn > 0) "لف يمين ${turn.toInt()}°" else "لف شمال ${(-turn).toInt()}°",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = if (aligned) Positive else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "القبلة ${qibla.toInt()}° من الشمال • المسافة لمكة ${Prayer.distanceToKaabaKm()} كم",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
        )
        Text(
            "امسك التليفون مفرود وبعيد عن الحديد." + if (accuracyLow) " البوصلة محتاجة معايرة: حرّك التليفون على شكل رقم 8." else "",
            style = MaterialTheme.typography.bodySmall, color = if (accuracyLow) Warn else MaterialTheme.colorScheme.outline,
        )
    }
}
