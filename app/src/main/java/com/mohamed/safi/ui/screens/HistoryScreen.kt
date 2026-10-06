package com.mohamed.safi.ui.screens

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
    val country = historyCountries[tab]
    val eras = when (country.first) { "egypt" -> History.egypt; "uae" -> History.uae; else -> emptyList() }

    ScreenScaffold("تاريخ الدول العربية والأندلس", onBack = onBack) { pad ->
        @Suppress("UNUSED_VARIABLE") val n = historyCountries.size
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, edgePadding = 8.dp) {
                historyCountries.forEachIndexed { i, (_, name) -> Tab(tab == i, { tab = i }, text = { Text(name) }) }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (country.first == "uae") item {
                    AppCard(color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Verified, null); Spacer(Modifier.width(8.dp))
                            Text("كتب الإمارات هنا من مصادر الدولة الرسمية فقط: البوابة الرسمية u.ae، ووزارة الخارجية، ومكتب دبي الإعلامي.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (country.first != "others" && country.first != "uae") item {
                    Text("كل دولة فيها نبذة شاملة من القديم لليوم (ويكيبيديا العربية)، ومعاها كتب التاريخ المتاحة عنها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
                item { SectionTitle("الكتب — تتقرا جوه التطبيق") }
                item { BookList(if (country.first == "others") listOf("history") else listOf(country.first)) { open("book/$it") } }
                if (country.first == "egypt" || country.first == "others") item { BookCard(com.mohamed.safi.faith.Books.bidaya) { open("bidaya") } }
                if (country.first == "uae") item {
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
                if (eras.isNotEmpty()) item { SectionTitle(if (country.first == "egypt") "خلاصة سريعة: من الفراعنة لليوم" else "خلاصة سريعة: من العصور القديمة لليوم") }
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

/** Country tabs: the book category for each (see Books.categories). "others" shows the general Islamic history books. */
private val historyCountries = listOf(
    "egypt" to "مصر", "uae" to "الإمارات", "saudi" to "السعودية", "kuwait" to "الكويت", "qatar" to "قطر", "bahrain" to "البحرين",
    "oman" to "عُمان", "yemen" to "اليمن", "iraq" to "العراق", "sham" to "سوريا", "lebanon" to "لبنان", "jordan" to "الأردن",
    "palestine" to "فلسطين والقدس", "sudan" to "السودان", "libya" to "ليبيا", "tunisia" to "تونس", "algeria" to "الجزائر",
    "maghrib" to "المغرب", "mauritania" to "موريتانيا", "somalia" to "الصومال", "djibouti" to "جيبوتي", "comoros" to "جزر القمر",
    "andalus" to "الأندلس", "others" to "التاريخ الإسلامي العام",
)
