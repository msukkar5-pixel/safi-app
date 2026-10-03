package com.mohamed.safi.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.*
import com.mohamed.safi.location.LocationLogger
import com.mohamed.safi.location.LocationService
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun durationText(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "مرور"
        min < 60 -> "$min دقيقة"
        else -> "${min / 60} ساعة" + if (min % 60 >= 5) " و${min % 60} دقيقة" else ""
    }
}

@Composable
fun PlacesScreen(onBack: () -> Unit) {
    val dao = SafiApp.db.dao()
    val prefs = SafiApp.prefs
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var day by remember { mutableStateOf(LocalDate.now(zone)) }
    val (from, to) = remember(day) { dayRange(day) }
    val stays by dao.locationsBetween(from, to).collectAsState(emptyList())
    val expenses by dao.expensesBetween(from, to).collectAsState(emptyList())
    var tracking by remember { mutableStateOf(prefs.locationOn) }
    var naming by remember { mutableStateOf<LocationLog?>(null) }
    var showPassing by remember { mutableStateOf(false) }
    val perms by rememberPermState()

    val shown = stays.filter { showPassing || it.endTime - it.startTime >= 5 * 60_000L }

    ScreenScaffold(
        "أماكني", onBack = onBack,
        actions = {
            IconButton(onClick = { showPassing = !showPassing }) {
                Icon(if (showPassing) Icons.Default.FilterAltOff else Icons.Default.FilterAlt, "إظهار المرور")
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("تسجيل الأماكن", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (!perms.location) "محتاج صلاحية الموقع"
                                else if (tracking) "بيسجل كل ${prefs.locationIntervalMin} دقايق" else "متوقف",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        Switch(checked = tracking && perms.location, onCheckedChange = { on ->
                            if (on && !perms.location) {
                                toast(ctx, "اسمح بالموقع من الإعدادات")
                            } else {
                                tracking = on
                                prefs.locationOn = on
                                if (on) LocationService.start(ctx) else LocationService.stop(ctx)
                            }
                        })
                    }
                    if (tracking && perms.location && !perms.bgLocation) {
                        Text("علشان يسجل والتطبيق مقفول اسمح بالموقع \"طول الوقت\" من الإعدادات.", style = MaterialTheme.typography.bodySmall, color = Warn)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { day = day.minusDays(1) }) { Icon(Icons.Default.ChevronRight, "اليوم اللي قبله") }
                    Text(shortDate(from) + " — " + dateStr(from).substringBefore(" "), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { day = day.plusDays(1) }, enabled = day.isBefore(LocalDate.now(zone))) { Icon(Icons.Default.ChevronLeft, "اليوم اللي بعده") }
                }
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Default.Place, "مفيش أماكن متسجلة اليوم ده") }
            items(shown, key = { it.id }) { s ->
                val here = expenses.filter { !it.isIncome && it.time in (s.startTime - 5 * 60_000L)..(s.endTime + 10 * 60_000L) }
                AppCard(onClick = {
                    val label = Uri.encode(s.placeName.ifBlank { "مكان" })
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${s.lat},${s.lng}?q=${s.lat},${s.lng}($label)")))
                    }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBadge(s.placeName, 40, Icons.Default.Place, Color2)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.placeName.ifBlank { "%.4f, %.4f".format(s.lat, s.lng) }, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(
                                "${timeStr(s.startTime)} → ${timeStr(s.endTime)} • ${durationText(s.endTime - s.startTime)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(onClick = { naming = s }) { Icon(Icons.Default.Edit, "سمّي المكان") }
                    }
                    if (here.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        here.forEach { e ->
                            Text("💳 ${money(e.amountAed)} — ${e.merchant.ifBlank { e.category }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (expenses.isNotEmpty()) {
                item {
                    val total = expenses.filter { !it.isIncome }.sumOf { it.amountAed }
                    Text("مصاريف اليوم: ${money(total)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    naming?.let { s ->
        var name by remember(s.id) { mutableStateOf(s.placeName) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text("سمّي المكان ده") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("مثلاً: البيت، الشغل، الجيم") }, singleLine = true)
                    Text("أي مرة تروح المكان ده هيتسجل بالاسم ده.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim()
                    if (n.isNotEmpty()) scope.launch { LocationLogger.nameHere(ctx, n, s.lat, s.lng) }
                    naming = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("إلغاء") } },
        )
    }
}
