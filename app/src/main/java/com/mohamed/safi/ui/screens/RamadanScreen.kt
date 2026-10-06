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
import kotlinx.coroutines.launch
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
            item { SectionTitle("تحدّي الخير النهارده") }
            item { DeedCard(now.toLocalDate()) }
            item { SectionTitle("خطة الختمة") }
            item { KhatmaPlanCard(open) }
            item { SectionTitle("دفتر رمضان") }
            item { NotebookCard(now.toLocalDate()) }
            item { SectionTitle("سجل الصدقات") }
            item { SadaqaCard() }
            item { SectionTitle("تنبيهات وبطاقة") }
            item { AlertsAndShare(inRamadan, day, maghrib, open) }
            item { SectionTitle("روابط مفيدة") }
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

@Composable
private fun DeedCard(d: java.time.LocalDate) {
    var done by remember(d) { mutableStateOf(Ramadan.deedDone(d)) }
    GoldCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💝", fontSize = 30.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(Ramadan.deed(d), fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("خلّصت ${Ramadan.deedsCount()} تحدّي لحد دلوقتي", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Checkbox(done, { done = it; Ramadan.setDeedDone(d, it) })
        }
    }
}

@Composable
private fun KhatmaPlanCard(open: (String) -> Unit) {
    var days by remember { mutableIntStateOf(Ramadan.khatmaPlanDays()) }
    AppCard {
        Text("تختم القرآن في كام يوم؟", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(30, 15, 10).forEach { n -> FilterChip(days == n, { days = n; Ramadan.setKhatmaPlan(n) }, label = { Text("$n يوم") }) }
        }
        Text("وردك: ${Wird.pagesPerDay} صفحة في اليوم • من صفحة ${Wird.nextPage}", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { open("wird") }) { Text("افتح الورد") }
    }
}

@Composable
private fun NotebookCard(d: java.time.LocalDate) {
    var text by remember(d) { mutableStateOf(Ramadan.note(d)) }
    var past by remember { mutableStateOf(false) }
    AppCard {
        Text(Ramadan.prompt(d), fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 19.sp)
        OutlinedTextField(text, { text = it; Ramadan.setNote(d, it) }, placeholder = { Text("اكتب براحتك…") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        Text("🔒 الدفتر ده ليك إنت بس، مش بيتشارك مع حد.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        val days = remember(past) { Ramadan.noteDays().filter { it != d } }
        if (days.isNotEmpty()) TextButton(onClick = { past = !past }) { Text(if (past) "اخفي الأيام اللي فاتت" else "الأيام اللي فاتت (${days.size})") }
        if (past) days.take(30).forEach { day ->
            Text("${Ramadan.prompt(day)} • ${com.mohamed.safi.data.dateStr(day.atStartOfDay(zone).toInstant().toEpochMilli())}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            androidx.compose.material3.Text(Ramadan.note(day))
        }
    }
}

@Composable
private fun SadaqaCard() {
    var list by remember { mutableStateOf(Ramadan.sadaqat()) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val total = remember(list) { Ramadan.sadaqaThisRamadan() }
    AppCard {
        Text("مجموع صدقاتك في رمضان: ${fmtAmount(total)}", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(amount, { amount = it.take(10) }, label = { Text("المبلغ") }, singleLine = true, modifier = Modifier.weight(1f),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
            OutlinedTextField(note, { note = it.take(80) }, label = { Text("لمين / ملاحظة") }, singleLine = true, modifier = Modifier.weight(1.4f))
        }
        Button(onClick = {
            val v = amount.toDoubleOrNull()
            if (v != null && v > 0) { Ramadan.addSadaqa(v, note); list = Ramadan.sadaqat(); amount = ""; note = "" }
        }, enabled = (amount.toDoubleOrNull() ?: 0.0) > 0, modifier = Modifier.fillMaxWidth()) { Text("سجّل") }
        list.take(5).forEach { x ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text("${fmtAmount(x.amount)}  ${x.note}", Modifier.weight(1f))
                Text(com.mohamed.safi.data.dateStr(x.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                IconButton(onClick = { Ramadan.removeSadaqa(x.at); list = Ramadan.sadaqat() }) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
            }
        }
        Text("السجل ده ليك بس عشان تتابع نفسك. وصدقة السر أفضل ما تتحكيش.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
    }
}

private fun fmtAmount(v: Double) = if (v % 1.0 == 0.0) "%,d".format(java.util.Locale.US, v.toLong()) else "%,.2f".format(java.util.Locale.US, v)

@Composable
private fun AlertsAndShare(inRamadan: Boolean, day: Int, maghrib: LocalDateTime?, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(FaithAlerts.suhoorOn && FaithAlerts.iftarOn) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("تنبيه السحور والإفطار", fontWeight = FontWeight.Bold)
                Text("قبل الفجر بـ ${FaithAlerts.suhoorMin} دقيقة، وقبل المغرب بـ ${FaithAlerts.iftarMin} دقيقة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Switch(on, {
                on = it; FaithAlerts.suhoorOn = it; FaithAlerts.iftarOn = it
                FaithAlerts.schedule(ctx, "suhoor"); FaithAlerts.schedule(ctx, "iftar")
            })
        }
        TextButton(onClick = { open("alerts") }) { Text("ظبط المواعيد") }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Text("بطاقة تشاركها مع العيلة والأصحاب: آية اليوم ومعاد المغرب.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { scope.launch {
            val surahs = runCatching { Quran.surahs(ctx) }.getOrNull().orEmpty()
            val pool = surahs.flatMap { s -> s.ayahs.filter { it.text.length in 60..200 }.map { s to it } }
            if (pool.isEmpty()) { toast(ctx, "مقدرتش أجهّز البطاقة"); return@launch }
            val (s, a) = pool[(java.time.LocalDate.now(zone).toEpochDay() % pool.size).toInt()]
            val title = if (inRamadan) tr("اليوم $day من رمضان 🌙") else tr("رمضان قرّب 🌙")
            val foot = listOfNotNull(maghrib?.let { tr("المغرب ${t12(it)}") }, Prayer.city.takeIf { it.isNotBlank() }).joinToString(" • ")
            ShareCard.share(ctx, title, "﴿ ${a.text} ﴾", "${s.name} • ${a.n}", foot)
        } }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("شارك بطاقة آية اليوم") }
    }
}
