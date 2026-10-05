package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamed.safi.data.zone
import com.mohamed.safi.faith.FaithAlerts
import com.mohamed.safi.faith.Prayer
import com.mohamed.safi.ui.*
import java.time.LocalDate
import java.time.LocalTime

private fun t12(t: LocalTime): String {
    val h = t.hour % 12 + if (t.hour % 12 == 0) 12 else 0
    return "$h:${t.minute.toString().padStart(2, '0')} ${if (t.hour < 12) "ص" else "م"}"
}

/** التنبيهات: every worship alert in one place, each with its own time. */
@Composable
fun AlertsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    fun changed(id: String? = null) { if (id == null) FaithAlerts.scheduleAll(ctx) else FaithAlerts.schedule(ctx, id); rev++ }
    fun pick(initial: LocalTime, onPicked: (LocalTime) -> Unit) {
        val ms = LocalDate.now(zone).atTime(initial).atZone(zone).toInstant().toEpochMilli()
        pickTime(ctx, ms) { h, m -> onPicked(LocalTime.of(h, m)) }
    }
    key(rev) {
        ScreenScaffold("التنبيهات", onBack = onBack) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // ------------------------------------------------ prayer
                SectionTitle("الأذان والصلاة")
                GoldCard {
                    SwitchRow("تنبيه وقت الأذان", "على مواعيد ${Prayer.city}", Prayer.alertsOn) {
                        Prayer.alertsOn = it; Prayer.preMinutes = 0; Prayer.schedule(ctx); rev++
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    SwitchRow("تنبيه قبل الأذان", "قبلها بـ ${FaithAlerts.preMin} دقيقة", FaithAlerts.preOn) { FaithAlerts.preOn = it; changed("pre") }
                    if (FaithAlerts.preOn) {
                        MinutesSlider("قبل الأذان بـ", FaithAlerts.preMin, 5..60) { FaithAlerts.preMin = it; changed("pre") }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FaithAlerts.PRAYERS.forEach { p ->
                                val on = p in FaithAlerts.prePrayers
                                FilterChip(on, {
                                    FaithAlerts.prePrayers = if (on) FaithAlerts.prePrayers - p else FaithAlerts.prePrayers + p; changed("pre")
                                }, label = { Text(p) })
                            }
                        }
                    }
                    NextLine("pre")
                    TestButton { FaithAlerts.notify(ctx, "pre", Prayer.nextPrayer().first, test = true) }
                }

                // ------------------------------------------------ adhkar
                SectionTitle("الأذكار")
                FaithAlerts.azkarSlots.forEach { s ->
                    GoldCard {
                        val on = FaithAlerts.azkarOn(s.id)
                        val fixed = FaithAlerts.azkarMode(s.id) == "fixed"
                        SwitchRow(
                            s.label,
                            if (fixed) "الساعة ${t12(FaithAlerts.azkarTime(s))}" else "بعد ${s.anchor} بـ ${FaithAlerts.azkarAfter(s)} دقيقة",
                            on,
                        ) { FaithAlerts.setAzkarOn(s.id, it); changed(s.id) }
                        if (on) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(!fixed, { FaithAlerts.setAzkarMode(s.id, "after"); changed(s.id) }, label = { Text("بعد ${s.anchor}") })
                                FilterChip(fixed, { FaithAlerts.setAzkarMode(s.id, "fixed"); changed(s.id) }, label = { Text("ساعة ثابتة") })
                            }
                            if (fixed) OutlinedButton(onClick = { pick(FaithAlerts.azkarTime(s)) { FaithAlerts.setAzkarTime(s.id, it); changed(s.id) } }) {
                                Icon(Icons.Default.Schedule, null); Text("  ${t12(FaithAlerts.azkarTime(s))}")
                            } else MinutesSlider("بعد ${s.anchor} بـ", FaithAlerts.azkarAfter(s), 0..180) { FaithAlerts.setAzkarAfter(s.id, it); changed(s.id) }
                            NextLine(s.id)
                        }
                        TestButton { FaithAlerts.notify(ctx, s.id, "", test = true) }
                    }
                }
                GoldCard {
                    SwitchRow("أذكار بعد كل صلاة", "بعد الصلاة بـ ${FaithAlerts.afterPrayerMin} دقيقة", FaithAlerts.afterPrayerOn) { FaithAlerts.afterPrayerOn = it; changed("after") }
                    if (FaithAlerts.afterPrayerOn) MinutesSlider("بعد الأذان بـ", FaithAlerts.afterPrayerMin, 5..60) { FaithAlerts.afterPrayerMin = it; changed("after") }
                    NextLine("after")
                }

                // ------------------------------------------------ wird
                SectionTitle("الورد اليومي")
                GoldCard {
                    SwitchRow("فكّرني بالورد", "الساعة ${t12(FaithAlerts.wirdTime)} — مش هيجيلك لو خلّصته", FaithAlerts.wirdOn) { FaithAlerts.wirdOn = it; changed() }
                    if (FaithAlerts.wirdOn) {
                        OutlinedButton(onClick = { pick(FaithAlerts.wirdTime) { FaithAlerts.wirdTime = it; changed("wird") } }) {
                            Icon(Icons.Default.Schedule, null); Text("  ${t12(FaithAlerts.wirdTime)}")
                        }
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        SwitchRow("تنبيه أخير لو لسه ما قريتش", "الساعة ${t12(FaithAlerts.wirdLastTime)}", FaithAlerts.wirdLastOn) { FaithAlerts.wirdLastOn = it; changed("wird_last") }
                        if (FaithAlerts.wirdLastOn) OutlinedButton(onClick = { pick(FaithAlerts.wirdLastTime) { FaithAlerts.wirdLastTime = it; changed("wird_last") } }) {
                            Icon(Icons.Default.Schedule, null); Text("  ${t12(FaithAlerts.wirdLastTime)}")
                        }
                        NextLine("wird")
                    }
                    TestButton { FaithAlerts.notify(ctx, "wird", "", test = true) }
                }
                Text("لو التنبيهات مش بتوصل في ميعادها: من الإعدادات اسمح بـ«المنبهات في ميعادها بالظبط» و«يشتغل في الخلفية».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Switch(on, onChange)
    }
}

@Composable
private fun MinutesSlider(label: String, value: Int, range: IntRange, onDone: (Int) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text("$label ${v.toInt()} دقيقة", style = MaterialTheme.typography.bodySmall)
        Slider(v, { v = (Math.round(it / 5f) * 5f).coerceIn(range.first.toFloat(), range.last.toFloat()) },
            valueRange = range.first.toFloat()..range.last.toFloat(), onValueChangeFinished = { onDone(v.toInt()) })
    }
}

@Composable
private fun NextLine(id: String) {
    val n = remember(id) { runCatching { FaithAlerts.next(id) }.getOrNull() }
    if (n != null) Text(
        "الجاي: " + (if (n.first.toLocalDate() == LocalDate.now(zone)) "النهارده" else "بكرة") + " ${t12(n.first.toLocalTime())}",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun TestButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) { Icon(Icons.Default.NotificationsActive, null, Modifier.size(18.dp)); Text(" جرّب التنبيه") }
}
