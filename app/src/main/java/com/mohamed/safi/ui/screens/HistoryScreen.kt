package com.mohamed.safi.ui.screens

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
import androidx.compose.ui.unit.sp
import com.mohamed.safi.faith.Era
import com.mohamed.safi.faith.History
import com.mohamed.safi.faith.Shaarawy
import com.mohamed.safi.faith.SourceLink
import com.mohamed.safi.ui.*

@Composable
fun HistoryScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val eras = if (tab == 0) History.egypt else History.uae

    ScreenScaffold("تاريخ مصر والإمارات", onBack = onBack) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("تاريخ مصر") })
                Tab(tab == 1, { tab = 1 }, text = { Text("تاريخ الإمارات") })
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == 1) item {
                    AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, null); Spacer(Modifier.width(8.dp))
                            Text("كتب الإمارات هنا من مصادر الدولة الرسمية فقط: البوابة الرسمية u.ae، ووزارة الخارجية، ومكتب دبي الإعلامي.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { SectionTitle("الكتب — تتقرا جوه التطبيق") }
                item { BookList(if (tab == 0) listOf("egypt") else listOf("uae")) { open("book/$it") } }
                if (tab == 0) item { BookCard(com.mohamed.safi.faith.Books.bidaya) { open("bidaya") } }
                if (tab == 1) item {
                    AppCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text("علمتني الحياة — الشيخ محمد بن راشد آل مكتوم", fontWeight = FontWeight.Bold)
                        Text("الكتاب كامل محفوظ الحقوق ومتاح في المكتبات بس (صدر ٢٥ سبتمبر ٢٠٢٥). اللي في التطبيق هو الإعلان الرسمي والمقتطفات اللي نشرها مكتب دبي الإعلامي.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { open("book/mbr_life") }) { Text("اقرأ المتاح") }
                            OutlinedButton(onClick = { Shaarawy.openUrl(ctx, "https://www.google.com/search?q=" + android.net.Uri.encode("شراء كتاب علمتني الحياة محمد بن راشد")) }) { Text("اشتريه") }
                        }
                    }
                }
                item { SectionTitle(if (tab == 0) "خلاصة سريعة: من الفراعنة لليوم" else "خلاصة سريعة: من العصور القديمة لليوم") }
                items(eras) { e ->
                    val k = "$tab:${e.title}"
                    EraCard(e, k in expanded) { expanded = if (k in expanded) expanded - k else expanded + k }
                }
                item {
                    OutlinedButton(onClick = { open("audiobooks") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(6.dp)); Text("كتب تاريخ مسموعة")
                    }
                }
            }
        }
    }
}

@Composable
private fun EraCard(e: Era, open: Boolean, toggle: () -> Unit) {
    AppCard(onClick = toggle) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.title, fontWeight = FontWeight.Bold)
                Text(e.period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            Text(e.body, style = MaterialTheme.typography.bodyMedium, lineHeight = 26.sp)
            e.points.forEach { p ->
                Row(Modifier.padding(top = 4.dp)) {
                    Text("• ", color = MaterialTheme.colorScheme.primary)
                    Text(p, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SourceCard(s: SourceLink, highlight: Boolean = false) {
    val ctx = LocalContext.current
    AppCard(
        onClick = { Shaarawy.openUrl(ctx, s.url) },
        color = if (highlight) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, fontWeight = FontWeight.Bold)
                Text(s.by, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (s.note.isNotBlank()) Text(s.note, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.OpenInNew, null)
        }
    }
}
