package com.mohamed.safi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.data.zone
import com.mohamed.safi.faith.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Ramadan dashboard: what now, iftar/suhoor countdown, daily plan, last ten nights. Outside Ramadan: a countdown and preparation. */
@Composable
fun RamadanScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now(zone)) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalDateTime.now(zone) } }
    var tick by remember { mutableIntStateOf(0) }
    val inRamadan = Ramadan.isRamadan(now.toLocalDate())
    val day = Ramadan.day(now.toLocalDate())
    val times = remember(now.toLocalDate()) { Prayer.today().times.toMap() }
    val fajr = times["الفجر"]; val maghrib = times["المغرب"]
    // the Laylat al-Qadr supplication, taken from the verified guide content (not typed here)
    val qadrDua = remember {
        runCatching { Deen.guide("hajj", ctx).sections.flatMap { it.blocks }.firstOrNull { it.t == "dua" && "عفو تحب العفو" in Quran.plain(it.x) } }.getOrNull()
    }

    ScreenScaffold(if (inRamadan) "رمضان" else "رمضان قرّب", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF1A2A4F), Color(0xFF3B4C8C))))) {
                    IslamicPattern(Modifier.matchParentSize(), GoldSoft.copy(alpha = 0.10f))
                    Column(Modifier.padding(20.dp)) {
                        if (inRamadan) {
                            Text("اليوم $day من رمضان 🌙", color = Color.White, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            LinearProgressIndicator(progress = { day / 30f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(6.dp).clip(CircleShape), color = Gold, trackColor = Color.White.copy(alpha = 0.2f), drawStopIndicator = {})
                            val iftarAhead = maghrib != null && now.isBefore(maghrib)
                            val target = if (iftarAhead) maghrib else fajr?.plusDays(if (fajr.isBefore(now)) 1L else 0L)
                            if (target != null) {
                                Text(if (iftarAhead) "الفطار (المغرب)" else "آخر السحور (الفجر)", color = Color.White.copy(alpha = 0.8f))
                                Text(leftText(java.time.Duration.between(now, target)), color = Gold, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                            }
                            Row(Modifier.padding(top = 6.dp)) {
                                fajr?.let { Text("الفجر ${t12(it)}  ", color = Color.White) }
                                maghrib?.let { Text("المغرب ${t12(it)}", color = Color.White) }
                            }
                        } else {
                            val left = Ramadan.daysUntil(now.toLocalDate())
                            Text(if (left > 0) "فاضل $left يوم على رمضان 🌙" else "رمضان", color = Color.White, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                            Text("التاريخ حسب تقويم أم القرى على تليفونك، وممكن يفرق يوم عن رؤية الهلال في بلدك.", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (inRamadan) {
                item {
                    val (title, sub) = Ramadan.whatNow(now)
                    var hidden by remember(title) { mutableStateOf(false) }
                    if (!hidden) GoldCard {
                        Text("أعمل إيه دلوقتي؟", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(title, fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(sub)
                        TextButton(onClick = { hidden = true }) { Text("تخطّي") }
                    }
                }
                item { SectionTitle("خطة اليوم") }
                item {
                    var mins by remember { mutableIntStateOf(Ramadan.planMinutes) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Ramadan.plans.keys.forEach { m -> FilterChip(mins == m, { mins = m; Ramadan.planMinutes = m }, label = { Text("$m دقيقة") }) }
                    }
                    Spacer(Modifier.height(6.dp))
                    @Suppress("UNUSED_VARIABLE") val t = tick
                    val done = Ramadan.done(now.toLocalDate())
                    AppCard {
                        (Ramadan.plans[mins] ?: emptyList()).forEach { item ->
                            CheckRow(item.icon, item.title, "p_" + item.id in done) { Ramadan.toggle("p_" + item.id, now.toLocalDate()); tick++ }
                        }
                    }
                }
                item { SectionTitle("متابعة اليوم") }
                item {
                    @Suppress("UNUSED_VARIABLE") val t = tick
                    val done = Ramadan.done(now.toLocalDate())
                    AppCard {
                        Ramadan.daily.forEach { item -> CheckRow(item.icon, item.title, item.id in done) { Ramadan.toggle(item.id, now.toLocalDate()); tick++ } }
                        Text("التطبيق بيشجّع بس، وعلى قد استطاعتك 🤍", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                if (Ramadan.lastTen(now.toLocalDate())) {
                    item { SectionTitle("العشر الأواخر") }
                    item {
                        GoldCard {
                            Text("ليالي القيام", fontWeight = FontWeight.Bold)
                            @Suppress("UNUSED_VARIABLE") val t = tick
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                (21..30).forEach { n ->
                                    FilterChip(Ramadan.nightDone(n), { Ramadan.setNight(n, !Ramadan.nightDone(n)); tick++ },
                                        label = { Text("$n" + if (n % 2 == 1) " ✨" else "") }, enabled = n <= day)
                                }
                            }
                            Text("✨ الليالي الوترية. ولو مش قادر على قيام طويل: ركعتين، وذكر، ودعاء.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    qadrDua?.let { b -> item { GuideBlockView(b, open) } }
                }
                item { SectionTitle("روابط مفيدة") }
            } else {
                item { SectionTitle("جهّز نفسك") }
                item {
                    AppCard {
                        listOf(
                            "📖 حدّد ختمتك: ٢٠ صفحة في اليوم = ختمة في رمضان",
                            "🤲 عوّد نفسك على أذكار الصباح والمساء من دلوقتي",
                            "💝 جهّز صدقتك وزكاتك",
                            "👨‍👩‍👧 فعّل متابعة الصيام للأطفال من مدينة الخير لو حابب",
                        ).forEach { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { open("wird") }, label = { Text("الورد والختمة") }, leadingIcon = { Icon(Icons.Default.AutoStories, null) })
                    AssistChip(onClick = { open("azkar") }, label = { Text("الأذكار") }, leadingIcon = { Icon(Icons.Default.Favorite, null) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { open("zakat") }, label = { Text("الزكاة") }, leadingIcon = { Icon(Icons.Default.Calculate, null) })
                    AssistChip(onClick = { open("kids") }, label = { Text("الأطفال") }, leadingIcon = { Icon(Icons.Default.ChildCare, null) })
                    AssistChip(onClick = { open("diary") }, label = { Text("دفتر يومي") }, leadingIcon = { Icon(Icons.Default.EditNote, null) })
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CheckRow(icon: String, title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (checked) Positive.copy(alpha = 0.08f) else Color.Transparent), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, { onToggle() })
        Text(icon, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(title, Modifier.weight(1f))
    }
}

/** Home card: in Ramadan the day and iftar countdown; in the 30 days before, a countdown. */
@Composable
fun RamadanHomeCard(open: (String) -> Unit) {
    val today = java.time.LocalDate.now(zone)
    val inR = Ramadan.isRamadan(today)
    val left = if (inR) 0 else Ramadan.daysUntil(today)
    if (!inR && (left <= 0 || left > 30)) return
    GoldCard(onClick = { open("ramadan") }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🌙", fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (inR) "اليوم ${Ramadan.day(today)} من رمضان" else "فاضل $left يوم على رمضان", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(if (inR) Ramadan.whatNow().second else "جهّز خطتك وختمتك من دلوقتي", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Icon(Icons.Default.ChevronLeft, null)
        }
    }
}
