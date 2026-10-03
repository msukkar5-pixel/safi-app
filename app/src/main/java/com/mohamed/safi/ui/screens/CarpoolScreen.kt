package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    if (!c.enabled || c.members.isEmpty()) return
    val today = LocalDate.now(zone)
    val next = c.nextWorkday(today.plusDays(1)) ?: return
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
    var editing by remember { mutableStateOf(cfg.members.isEmpty()) }
    var dayAction by remember { mutableStateOf<LocalDate?>(null) }
    fun save(c: CarpoolConfig) { cfg = c; Carpool.save(ctx, c) }

    ScreenScaffold(
        "دور السواقة", onBack = onBack,
        actions = { IconButton(onClick = { editing = true }) { Icon(Icons.Default.Settings, "الإعدادات") } },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (cfg.members.isEmpty()) {
                item { EmptyState(Icons.Default.DirectionsCar, "ضيف الأسامي من الإعدادات فوق") }
            } else {
                item {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("التنبيه", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "كل يوم الساعة ${"%d:%02d".format(cfg.hour, cfg.minute)}، لو بكرة دورك",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                                )
                            }
                            Switch(cfg.enabled, { save(cfg.copy(enabled = it)) })
                        }
                        val mine = cfg.myDays(LocalDate.now(zone), 30)
                        if (mine.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Text("دورك الجاي: ${Carpool.dayLabel(mine.first())} • ${mine.size} مرات في الـ 30 يوم الجايين", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item { SectionTitle("الأسبوعين الجايين") }
                item { Text("دوس على أي يوم علشان تبدّل السواق أو تخليه إجازة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                val days = (0 until 21).map { LocalDate.now(zone).plusDays(it.toLong()) }.filter { it.dayOfWeek.value in cfg.days }
                items(days, key = { it.toString() }) { d ->
                    val driver = cfg.driverFor(d)
                    val mine = driver != null && driver == cfg.myName
                    val skipped = d in cfg.skips
                    val overridden = d in cfg.overrides
                    AppCard(onClick = { dayAction = d }, color = if (mine) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(Carpool.dayLabel(d), fontWeight = FontWeight.SemiBold)
                                Text("${d.dayOfMonth}/${d.monthValue}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            when {
                                skipped -> Pill("إجازة", MaterialTheme.colorScheme.outline)
                                driver != null -> {
                                    if (overridden) { Pill("تبديل", Warn); Spacer(Modifier.width(6.dp)) }
                                    Text(if (mine) "انت 🚗" else driver, fontWeight = FontWeight.Bold, color = if (mine) Warn else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editing) CarpoolSetup(cfg, onDismiss = { editing = false }) { save(it); editing = false }

    dayAction?.let { d ->
        AlertDialog(
            onDismissRequest = { dayAction = null },
            title = { Text("${Carpool.dayLabel(d)} — ${d.dayOfMonth}/${d.monthValue}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("مين هيسوق اليوم ده؟", style = MaterialTheme.typography.labelLarge)
                    cfg.members.forEach { m ->
                        OutlinedButton(onClick = {
                            save(cfg.copy(overrides = cfg.overrides + (d to m), skips = cfg.skips - d))
                            dayAction = null
                        }, modifier = Modifier.fillMaxWidth()) { Text(m) }
                    }
                    TextButton(onClick = { save(cfg.copy(skips = cfg.skips + d, overrides = cfg.overrides - d)); dayAction = null }) {
                        Text("إجازة (محدش هيسوق، والدور يكمل بعدها)")
                    }
                    if (d in cfg.overrides || d in cfg.skips) {
                        TextButton(onClick = { save(cfg.copy(skips = cfg.skips - d, overrides = cfg.overrides - d)); dayAction = null }) {
                            Text("رجّعه للترتيب العادي")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dayAction = null }) { Text("إغلاق") } },
        )
    }
}

@Composable
private fun CarpoolSetup(cfg: CarpoolConfig, onDismiss: () -> Unit, onSave: (CarpoolConfig) -> Unit) {
    val ctx = LocalContext.current
    var names by remember { mutableStateOf(if (cfg.members.isEmpty()) listOf(com.mohamed.safi.SafiApp.prefs.userName, "", "", "") else cfg.members) }
    var me by remember { mutableIntStateOf(cfg.me) }
    var days by remember { mutableStateOf(cfg.days) }
    var startDate by remember { mutableLongStateOf(cfg.nextWorkday(LocalDate.now(zone).plusDays(1))?.millisAt(9) ?: LocalDate.now(zone).plusDays(1).millisAt(9)) }
    var startWho by remember {
        mutableIntStateOf(
            cfg.nextWorkday(LocalDate.now(zone).plusDays(1))?.let { d -> cfg.driverFor(d)?.let { cfg.members.indexOf(it) } }?.coerceAtLeast(0) ?: 0,
        )
    }
    var time by remember { mutableLongStateOf(LocalDate.now(zone).atTime(cfg.hour, cfg.minute).millis()) }
    var always by remember { mutableStateOf(cfg.alwaysNotify) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إعداد دور السواقة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                run { Text("الأسامي بالترتيب اللي بتلفوا بيه:", style = MaterialTheme.typography.labelLarge) }
                names.indices.forEach { i ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = me == i, onClick = { me = i })
                        OutlinedTextField(
                            names[i], { v -> names = names.toMutableList().also { it[i] = v } },
                            label = { Text(if (me == i) "انت" else "الشخص ${i + 1}") }, singleLine = true, modifier = Modifier.weight(1f),
                        )
                        if (names.size > 2) IconButton(onClick = {
                            names = names.toMutableList().also { it.removeAt(i) }
                            if (me >= names.size) me = 0
                        }) { Icon(Icons.Default.Close, "شيل") }
                    }
                }
                run {
                    Row {
                        TextButton(onClick = { names = names + "" }) { Icon(Icons.Default.Add, null); Text("ضيف شخص") }
                    }
                    Text("الدايرة جنب الاسم = انت", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                run {
                    Text("أيام الشغل", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        dowNames.forEach { (d, n) ->
                            FilterChip(
                                selected = d in days, onClick = { days = if (d in days) days - d else days + d },
                                label = { Text(n.take(3)) }, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                run {
                    DateField("أول يوم في الترتيب", startDate, withTime = false) { startDate = it }
                    Spacer(Modifier.height(6.dp))
                    val clean = names.map { it.trim() }.filter { it.isNotEmpty() }
                    if (clean.isNotEmpty()) {
                        ChoiceField("مين هيسوق اليوم ده", clean.getOrElse(startWho) { clean.first() }, clean) { startWho = clean.indexOf(it) }
                    }
                }
                run {
                    OutlinedButton(onClick = {
                        pickTime(ctx, time) { h, m -> time = LocalDate.now(zone).atTime(h, m).millis() }
                    }, modifier = Modifier.fillMaxWidth()) { Text("نبهني الساعة ${timeStr(time)}") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("نبهني كمان لما يكون مش دوري", Modifier.weight(1f))
                        Switch(always, { always = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val idxMap = names.mapIndexedNotNull { i, n -> if (n.isBlank()) null else i }
                val clean = idxMap.map { names[it].trim() }
                if (clean.size < 2 || days.isEmpty()) {
                    toast(ctx, "اكتب اسمين على الأقل واختار أيام الشغل")
                } else {
                    val t = time.toLdt()
                    onSave(
                        cfg.copy(
                            members = clean, me = idxMap.indexOf(me).coerceAtLeast(0), days = days,
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
