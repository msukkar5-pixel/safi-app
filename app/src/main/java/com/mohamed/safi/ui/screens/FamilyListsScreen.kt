package com.mohamed.safi.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mohamed.safi.data.zone
import com.mohamed.safi.family.Family
import com.mohamed.safi.family.FamilyLists
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import androidx.compose.material3.Text as RawText

/** The family's shared shopping list and dates, synced between the family's phones. */
@Composable
fun FamilyListsScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<FamilyLists.Event?>(null) }
    DisposableEffect(Family.joined) {
        if (Family.joined) Family.startLan(ctx)
        onDispose { Family.stopLan() }
    }
    ScreenScaffold("لستة العيلة", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            @Suppress("UNUSED_VARIABLE") val live = FamilyLists.version.intValue + Family.version.intValue
            item { TabRow(tab) { listOf("المشتريات", "مواعيد العيلة").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } } }
            if (tab == 0) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(text, { text = it.take(60) }, label = { Text("محتاجين إيه؟") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(qty, { qty = it.take(12) }, label = { Text("الكمية") }, singleLine = true, modifier = Modifier.width(90.dp))
                        FilledIconButton(onClick = { if (text.isNotBlank()) { FamilyLists.addItem(text, qty); text = ""; qty = "" } }) { Icon(Icons.Default.Add, null) }
                    }
                }
                val items = FamilyLists.items()
                if (items.isEmpty()) item { EmptyState(Icons.Default.ShoppingCart, "اللستة فاضية") }
                items(items, key = { it.id }) { i ->
                    AppCard(onClick = { FamilyLists.toggle(i) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(i.done, { FamilyLists.toggle(i) })
                            Column(Modifier.weight(1f)) {
                                RawText(i.text + if (i.qty.isNotBlank()) " • ${i.qty}" else "", fontWeight = FontWeight.SemiBold, textDecoration = if (i.done) TextDecoration.LineThrough else null)
                                if (i.by.isNotBlank()) RawText(i.by, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            IconButton(onClick = { FamilyLists.delete("shop", i.id) }) { Icon(Icons.Default.Close, null, Modifier.size(18.dp)) }
                        }
                    }
                }
                if (items.any { it.done }) item { TextButton(onClick = { FamilyLists.clearBought() }) { Text("امسح اللي اتشترى") } }
            } else {
                item { Button(onClick = { editing = FamilyLists.Event("", "", LocalDate.now(zone).toString(), FamilyLists.kinds[1], "", "") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("ضيف ميعاد أو مناسبة") } }
                val evs = FamilyLists.events()
                if (evs.isEmpty()) item { EmptyState(Icons.Default.Cake, "ضيفوا أعياد الميلاد والمواعيد المهمة، وهتوصل تنبيهات للعيلة كلها") }
                items(evs, key = { it.id }) { e ->
                    val d = e.nextDate()
                    val days = d?.let { ChronoUnit.DAYS.between(LocalDate.now(zone), it) }
                    AppCard(onClick = { editing = e }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RawText(if (e.kind == "عيد ميلاد") "🎂" else "📅", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                RawText(e.title, fontWeight = FontWeight.Bold)
                                RawText(listOf(tr(e.kind), d?.toString().orEmpty(), e.note).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            days?.let { Pill(when { it == 0L -> tr("النهارده"); it == 1L -> tr("بكرة"); it < 0 -> tr("فات"); else -> tr("بعد $it يوم") }) }
                        }
                    }
                }
            }
            item {
                AppCard {
                    if (!Family.joined) {
                        Text("اربطوا موبايلات العيلة عشان اللستة تتشارك بينكم.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { open("family") }) { Text("ربط العيلة") }
                    } else {
                        Text("على نفس الواي فاي بتتحدّث لوحدها وانت فاتح الشاشة. برّا البيت ابعت التحديث.", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            scope.launch {
                                val card = Family.myCard()
                                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, card), tr("ابعت التحديث")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }) { Icon(Icons.Default.Send, null); Text("ابعت التحديث") }
                    }
                }
            }
        }
    }
    editing?.let { e0 ->
        var e by remember(e0) { mutableStateOf(e0) }
        var del by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("ميعاد / مناسبة") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(e.title, { e = e.copy(title = it.take(50)) }, label = { Text("العنوان (مثلاً: عيد ميلاد مريم)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) { FamilyLists.kinds.forEach { k -> FilterChip(e.kind == k, { e = e.copy(kind = k) }, label = { Text(k) }) } }
                DateField("التاريخ", runCatching { LocalDate.parse(e.date).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull(), withTime = false) { ms ->
                    e = e.copy(date = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString())
                }
                OutlinedTextField(e.note, { e = e.copy(note = it.take(100)) }, label = { Text("ملاحظة") }, modifier = Modifier.fillMaxWidth())
                if (e0.id.isNotBlank()) TextButton(onClick = { del = true }) { Text("امسح", color = Danger) }
            }
        }, confirmButton = {
            Button(onClick = { if (e.title.isBlank()) toast(ctx, "اكتب العنوان") else { FamilyLists.saveEvent(e); editing = null } }) { Text("حفظ") }
        }, dismissButton = { TextButton(onClick = { editing = null }) { Text("إلغاء") } })
        if (del) ConfirmDialog("تمسحه؟", "", "امسح", { del = false }) { FamilyLists.delete("events", e0.id); del = false; editing = null }
    }
}
