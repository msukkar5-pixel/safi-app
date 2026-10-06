package com.mohamed.safi.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamed.safi.faith.Hadith
import com.mohamed.safi.faith.HadithBook
import com.mohamed.safi.faith.HadithSection
import com.mohamed.safi.faith.Hadiths
import com.mohamed.safi.ui.*

@Composable
fun HadithScreen(onBack: () -> Unit) {
    var bookId by remember { mutableStateOf<String?>(null) }
    var showFav by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf<Hadith?>(null) }
    var initialQuery by remember { mutableStateOf(UiBus.pendingHadith.value ?: "") }
    LaunchedEffect(Unit) { UiBus.pendingHadith.value?.let { bookId = "bukhari"; UiBus.pendingHadith.value = null } }
    LaunchedEffect(Unit) { today = Hadiths.ofTheDay() }

    val b = bookId
    if (b != null) {
        BackHandler { bookId = null; initialQuery = "" }
        HadithBookView(b, initialQuery) { bookId = null; initialQuery = "" }
        return
    }
    if (showFav) {
        BackHandler { showFav = false }
        FavouritesView { showFav = false }
        return
    }

    ScreenScaffold("الأحاديث الصحيحة", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            today?.let { h ->
                item {
                    Text("حديث اليوم", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    HadithCard(h)
                }
            }
            items(Hadiths.books) { (id, title) ->
                AppCard(onClick = { bookId = id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📚", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(
                                if (id == "bukhari") "الإمام محمد بن إسماعيل البخاري" else "الإمام مسلم بن الحجاج النيسابوري",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
            item {
                AppCard(onClick = { showFav = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("المحفوظة (${Hadiths.favourites.size})", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            item {
                Text(
                    "المكتبة فيها الصحيحين بس (البخاري ومسلم)، وكل أحاديثهم صحيحة باتفاق العلماء. النص من قاعدة بيانات hadith-api المفتوحة.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun HadithBookView(id: String, initialQuery: String = "", onBack: () -> Unit) {
    var book by remember { mutableStateOf<HadithBook?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var section by remember { mutableStateOf<HadithSection?>(null) }
    var q by remember { mutableStateOf(initialQuery) }
    var bookSwitch by remember { mutableStateOf(id) }
    LaunchedEffect(attempt) {
        error = null
        try { book = Hadiths.load(id) } catch (e: Exception) { error = "محتاج إنترنت أول مرة علشان الكتاب يتحمّل" }
    }
    val bk = book
    val s = section
    if (bk != null && s != null) {
        BackHandler { section = null }
        SectionView(bk, s) { section = null }
        return
    }

    ScreenScaffold(Hadiths.bookTitle(id), onBack = onBack) { pad ->
        if (bk == null) {
            Column(Modifier.fillMaxSize().padding(pad).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (error == null) { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("بيفتح الكتاب…") }
                else { Text(error!!); Button(onClick = { attempt++ }) { Text("حاول تاني") } }
            }
            return@ScreenScaffold
        }
        val results = remember(q, bk) { Hadiths.search(bk, q) }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                OutlinedTextField(
                    q, { q = it }, placeholder = { Text("دوّر بكلمة: الصلاة، الصدقة، بر الوالدين…") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(),
                )
                Text("${bk.hadiths.size} حديث • ${bk.sections.size} كتاب", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            if (q.length >= 2) {
                item { Text("${results.size}${if (results.size >= 150) "+" else ""} نتيجة", fontWeight = FontWeight.SemiBold) }
                items(results) { h -> HadithCard(h, bk.sections.firstOrNull { it.number == h.section }?.name) }
            } else {
                items(bk.sections, key = { it.number }) { sec ->
                    AppCard(onClick = { section = sec }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${sec.number}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(36.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sec.name, fontWeight = FontWeight.SemiBold)
                                if (sec.last > 0) Text("الأحاديث ${sec.first} – ${sec.last}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionView(book: HadithBook, section: HadithSection, onBack: () -> Unit) {
    val list = remember(section) { book.hadiths.filter { it.section == section.number } }
    ReadingTheme { ScreenScaffold(section.name, onBack = onBack, actions = { ReadingSettingsButton() }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list) { h -> HadithCard(h) }
        }
    } }
}

@Composable
private fun FavouritesView(onBack: () -> Unit) {
    var list by remember { mutableStateOf<List<Hadith>?>(null) }
    LaunchedEffect(Unit) {
        val fav = Hadiths.favourites
        list = Hadiths.books.flatMap { (id, _) ->
            if (fav.none { it.startsWith("$id:") }) emptyList() else runCatching { Hadiths.load(id).hadiths.filter { Hadiths.key(it) in fav } }.getOrDefault(emptyList())
        }
    }
    ReadingTheme { ScreenScaffold("الأحاديث المحفوظة", onBack = onBack, actions = { ReadingSettingsButton() }) { pad ->
        val l = list
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (l == null) item { CircularProgressIndicator() }
            else if (l.isEmpty()) item { EmptyState(Icons.Default.BookmarkBorder, "دوس على علامة الحفظ في أي حديث") }
            else items(l) { h -> HadithCard(h) }
        }
    } }
}

@Composable
fun HadithCard(h: Hadith, sectionName: String? = null) {
    val ctx = LocalContext.current
    var fav by remember(h) { mutableStateOf(Hadiths.key(h) in Hadiths.favourites) }
    val ref = "${Hadiths.bookTitle(h.book)} — رقم ${h.number}"
    AppCard {
        Text(h.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 30.sp, textAlign = TextAlign.Justify)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ref, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (sectionName != null) Text(sectionName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = {
                val k = Hadiths.key(h)
                Hadiths.favourites = if (fav) Hadiths.favourites - k else Hadiths.favourites + k
                fav = !fav
            }) { Icon(if (fav) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "احفظ") }
            IconButton(onClick = {
                ShareBus.open("🌿", h.text, ref)
            }) { Icon(Icons.Default.Share, "شارك") }
        }
    }
}
