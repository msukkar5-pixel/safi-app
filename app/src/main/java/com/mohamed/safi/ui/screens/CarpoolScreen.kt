package com.mohamed.safi.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.mohamed.safi.data.*
import com.mohamed.safi.ui.*
import java.time.LocalDate

private val dowNames = listOf(1 to "اثنين", 2 to "ثلاثاء", 3 to "أربعاء", 4 to "خميس", 5 to "جمعة", 6 to "سبت", 7 to "أحد")

@Composable
fun CarpoolCard(open: (String) -> Unit) {
    val c = remember { Carpool.load() }
    if (!c.enabled) return
    val today = LocalDate.now(zone)
    val next = c.nextDriveDay(today.plusDays(1)) ?: return
    val driver = c.driverFor(next) ?: return
    val mine = driver == c.myName
    AppCard(onClick = { open("carpool") }, color = if (mine) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CatBadge("car", 40, Icons.Default.DirectionsCar, if (mine) Warn else Color2)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (mine) "${Carpool.dayLabel(next)} انت اللي هتسوق" else "${Carpool.dayLabel(next)} $driver اللي هيسوق",
                    fontWeight = FontWeight.Bold,
                )
                c.driverFor(today)?.let { Text("النهارده: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            }
        }
    }
}

@Composable
fun CarpoolScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(Carpool.load(ctx)) }
    var setup by remember { mutableStateOf(false) }
    var paste by remember { mutableStateOf(false) }
    var dayAction by remember { mutableStateOf<LocalDate?>(null) }
    var clearTable by remember { mutableStateOf(false) }
    fun save(c: CarpoolConfig) { cfg = c; Carpool.save(ctx, c) }

    ScreenScaffold(
        "دور السواقة", onBack = onBack,
        actions = { IconButton(onClick = { setup = true }) { Icon(Icons.Default.Settings, "الإعدادات") } },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(cfg.mode == "manual", { save(cfg.copy(mode = "manual")) }, label = { Text("جدول يدوي") }, leadingIcon = { Icon(Icons.Default.EditCalendar, null) })
                        FilterChip(cfg.mode == "auto", { save(cfg.copy(mode = "auto")) }, label = { Text("أوتوماتيك") }, leadingIcon = { Icon(Icons.Default.Autorenew, null) })
                    }
                    Text(
                        if (cfg.mode == "manual") "الجدول اللي اتفقتوا عليه بالتاريخ." + if (cfg.repeatManual) " لما يخلص بيتكرر من الأول." else ""
                        else "كل يوم شغل واحد بالترتيب، من ${shortDate(cfg.anchorDate.millisAt(9))} بـ ${cfg.members.getOrElse(cfg.anchorIndex) { "-" }}.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("التنبيه", fontWeight = FontWeight.SemiBold)
                            Text("الساعة ${"%d:%02d".format(cfg.hour, cfg.minute)} بالليل لو بكرة دورك", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(cfg.enabled, { save(cfg.copy(enabled = it)) })
                    }
                    val mine = cfg.myDays(LocalDate.now(zone), 30)
                    if (mine.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("دورك الجاي: ${Carpool.dayLabel(mine.first())} • ${mine.size} مرات في الـ 30 يوم الجايين", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (cfg.mode == "manual") item {
                AppCard {
                    Text("الجدول اليدوي", fontWeight = FontWeight.Bold)
                    Text(
                        if (cfg.manual.isEmpty()) "لسه فاضي" else "${cfg.manual.size} يوم • لحد ${shortDate(cfg.manualLast!!.millisAt(9))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { paste = true }) { Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(4.dp)); Text("الصق جدول") }
                        if (cfg.manual.isNotEmpty()) OutlinedButton(onClick = { clearTable = true }) { Text("امسح الجدول") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("لما الجدول يخلص، كرره من الأول", Modifier.weight(1f))
                        Switch(cfg.repeatManual, { save(cfg.copy(repeatManual = it)) })
                    }
                    Text("دوس على أي يوم تحت علشان تغيّر السواق بتاعه.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item { SectionTitle("الـ 4 أسابيع الجايين") }
            val days = (0 until 28).map { LocalDate.now(zone).plusDays(it.toLong()) }
                .filter { d -> cfg.driverFor(d) != null || d in cfg.skips || (cfg.mode == "auto" && d.dayOfWeek.value in cfg.days) || (cfg.mode == "manual" && d.dayOfWeek.value in 1..4) }
            items(days, key = { it.toString() }) { d ->
                val driver = cfg.driverFor(d)
                val mine = driver != null && driver == cfg.myName
                val skipped = d in cfg.skips
                val swapped = d in cfg.overrides
                AppCard(onClick = { dayAction = d }, color = if (mine) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Carpool.dayLabel(d), fontWeight = FontWeight.SemiBold)
                            Text("${d.dayOfMonth}/${d.monthValue}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        when {
                            skipped -> Pill("إجازة", MaterialTheme.colorScheme.outline)
                            driver != null -> {
                                if (swapped) { Pill("تبديل", Warn); Spacer(Modifier.width(6.dp)) }
                                Text(if (mine) "انت 🚗" else driver, fontWeight = FontWeight.Bold, color = if (mine) Warn else MaterialTheme.colorScheme.onSurface)
                            }
                            else -> Text("مش متحدد", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }

    if (setup) CarpoolSetup(cfg, onDismiss = { setup = false }) { save(it); setup = false }
    if (paste) PasteTableDialog(cfg, onDismiss = { paste = false }) { table, replace ->
        val newNames = table.values.distinct().filter { it !in cfg.members }
        save(cfg.copy(mode = "manual", members = cfg.members + newNames, manual = if (replace) table else cfg.manual + table, enabled = true))
        paste = false
    }
    if (clearTable) ConfirmDialog("امسح الجدول اليدوي؟", "${cfg.manual.size} يوم", "امسح", { clearTable = false }) { save(cfg.copy(manual = emptyMap())) }

    dayAction?.let { d ->
        AlertDialog(
            onDismissRequest = { dayAction = null },
            title = { Text("${Carpool.dayLabel(d)} — ${d.dayOfMonth}/${d.monthValue}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("مين هيسوق اليوم ده؟", style = MaterialTheme.typography.labelLarge)
                    cfg.members.forEach { m ->
                        OutlinedButton(onClick = {
                            save(
                                // Dates outside the manual table become overrides, so the table isn't stretched (and the gap blanked).
                                if (cfg.mode == "manual" && cfg.inManualRange(d)) cfg.copy(manual = cfg.manual + (d to m), overrides = cfg.overrides - d, skips = cfg.skips - d)
                                else cfg.copy(overrides = cfg.overrides + (d to m), skips = cfg.skips - d),
                            )
                            dayAction = null
                        }, modifier = Modifier.fillMaxWidth()) { Text(m + if (m == cfg.myName) " (انت)" else "") }
                    }
                    TextButton(onClick = { save(cfg.copy(skips = cfg.skips + d, overrides = cfg.overrides - d)); dayAction = null }) {
                        Text("إجازة — محدش هيسوق")
                    }
                    if (d in cfg.overrides || d in cfg.skips || (cfg.mode == "manual" && d in cfg.manual)) {
                        TextButton(onClick = {
                            save(cfg.copy(skips = cfg.skips - d, overrides = cfg.overrides - d, manual = if (cfg.mode == "manual" && cfg.inManualRange(d) && d !in cfg.overrides && d !in cfg.skips) cfg.manual - d else cfg.manual))
                            dayAction = null
                        }) { Text("امسح التعديل") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dayAction = null }) { Text("إغلاق") } },
        )
    }
}

@Composable
private fun PasteTableDialog(cfg: CarpoolConfig, onDismiss: () -> Unit, onSave: (Map<LocalDate, String>, Boolean) -> Unit) {
    val ctx = LocalContext.current
    var text by remember {
        mutableStateOf(
            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: "",
        )
    }
    var replace by remember { mutableStateOf(true) }
    val parsed = remember(text) { CarpoolConfig.parseTable(text, cfg.members) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الصق الجدول") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("انسخ الجدول من الجروب والصقه هنا. كل سطر فيه تاريخ واسم، مثلاً: الاتنين 5/10: صبحي", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(text, { text = it }, minLines = 5, maxLines = 12, modifier = Modifier.fillMaxWidth())
                Text("لقيت ${parsed.size} يوم:", fontWeight = FontWeight.SemiBold)
                parsed.entries.take(40).forEach { (d, n) ->
                    Text("${dateStr(d.millisAt(9))}: $n", style = MaterialTheme.typography.bodySmall)
                }
                val unknown = parsed.values.toSet() - cfg.members.toSet()
                if (unknown.isNotEmpty()) Text("أسامي جديدة هتتضاف: ${unknown.joinToString("، ")}", color = Warn, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("استبدل الجدول القديم", Modifier.weight(1f)); Switch(replace, { replace = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (parsed.isEmpty()) toast(ctx, "مفيش أيام مفهومة في النص") else onSave(parsed, replace) }) {
                Text("حفظ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun CarpoolSetup(cfg: CarpoolConfig, onDismiss: () -> Unit, onSave: (CarpoolConfig) -> Unit) {
    val ctx = LocalContext.current
    var names by remember { mutableStateOf(if (cfg.members.isEmpty()) listOf("", "", "", "") else cfg.members) }
    var me by remember { mutableIntStateOf(cfg.me) }
    var days by remember { mutableStateOf(cfg.days) }
    var startDate by remember { mutableLongStateOf(cfg.anchorDate.millisAt(9)) }
    var startWho by remember { mutableIntStateOf(cfg.anchorIndex) }
    var time by remember { mutableLongStateOf(LocalDate.now(zone).atTime(cfg.hour, cfg.minute).millis()) }
    var always by remember { mutableStateOf(cfg.alwaysNotify) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعداد دور السواقة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("الأسامي بالترتيب (للأوتوماتيك):", style = MaterialTheme.typography.labelLarge)
                names.indices.forEach { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = me == i, onClick = { me = i })
                        OutlinedTextField(
                            names[i], { v -> names = names.toMutableList().also { it[i] = v } },
                            label = { Text(if (me == i) "انت" else "الشخص ${i + 1}") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                        Column {
                            IconButton(onClick = { if (i > 0) names = names.toMutableList().also { val t = it[i]; it[i] = it[i - 1]; it[i - 1] = t }.also { if (me == i) me = i - 1 else if (me == i - 1) me = i } }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, "فوق")
                            }
                            IconButton(onClick = { if (i < names.size - 1) names = names.toMutableList().also { val t = it[i]; it[i] = it[i + 1]; it[i + 1] = t }.also { if (me == i) me = i + 1 else if (me == i + 1) me = i } }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, "تحت")
                            }
                        }
                    }
                }
                Row {
                    TextButton(onClick = { names = names + "" }) { Icon(Icons.Default.Add, null); Text("ضيف شخص") }
                    if (names.size > 2) TextButton(onClick = { names = names.dropLast(1); if (me >= names.size) me = 0 }) { Text("شيل آخر واحد") }
                }
                Text("الدايرة جنب الاسم = انت", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                HorizontalDivider()
                Text("الأوتوماتيك: أيام الشغل", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    dowNames.forEach { (d, n) ->
                        FilterChip(selected = d in days, onClick = { days = if (d in days) days - d else days + d }, label = { Text(n.take(3)) }, modifier = Modifier.weight(1f))
                    }
                }
                DateField("الأوتوماتيك يبدأ من", startDate, withTime = false) { startDate = it }
                val clean = names.map { it.trim() }.filter { it.isNotEmpty() }
                if (clean.isNotEmpty()) {
                    ChoiceField("أول واحد يسوق", clean.getOrElse(startWho) { clean.first() }, clean) { startWho = clean.indexOf(it) }
                    Text("وبعده بالترتيب: " + (1 until clean.size).joinToString(" ← ") { clean[(startWho + it) % clean.size] }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                HorizontalDivider()
                OutlinedButton(onClick = { pickTime(ctx, time) { h, m -> time = LocalDate.now(zone).atTime(h, m).millis() } }, modifier = Modifier.fillMaxWidth()) {
                    Text("نبهني الساعة ${timeStr(time)}")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("نبهني كمان لما يكون مش دوري", Modifier.weight(1f)); Switch(always, { always = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val idx = names.mapIndexedNotNull { i, n -> if (n.isBlank()) null else i }
                val clean = idx.map { names[it].trim() }
                if (clean.size < 2) toast(ctx, "اكتب اسمين على الأقل") else {
                    val t = time.toLdt()
                    onSave(
                        cfg.copy(
                            members = clean, me = idx.indexOf(me).coerceAtLeast(0), days = days.ifEmpty { setOf(1, 2, 3, 4) },
                            anchorDate = startDate.toLocalDate(), anchorIndex = startWho.coerceIn(0, clean.size - 1),
                            hour = t.hour, minute = t.minute, alwaysNotify = always, enabled = true,
                        ),
                    )
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
