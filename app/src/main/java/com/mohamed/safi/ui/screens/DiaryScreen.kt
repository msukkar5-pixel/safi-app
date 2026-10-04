package com.mohamed.safi.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.data.*
import com.mohamed.safi.diary.DiaryAI
import com.mohamed.safi.diary.DiaryDb
import com.mohamed.safi.diary.DiaryEntry
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch

private val moods = listOf("😄", "🙂", "😐", "😔", "😢", "😠", "😴", "🤒", "🙏")

@Composable
fun DiaryScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by DiaryDb.dao.all().collectAsState(emptyList())
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<DiaryEntry?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var events by remember { mutableStateOf(DiaryAI.lastEvents) }
    var personality by remember { mutableStateOf(DiaryAI.lastPersonality) }
    var showReport by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun run(kind: String, block: suspend () -> String) {
        if (!Claude.hasKey) { toast(ctx, "اربط ذكاء اصطناعي من الإعدادات"); return }
        busy = kind
        scope.launch {
            try { val r = block(); showReport = kind to r } catch (e: Exception) { toast(ctx, e.message ?: "فشل") }
            busy = null
        }
    }

    ScreenScaffold(
        "مذكراتي", onBack = onBack,
        fab = { ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Default.Mic, null) }, text = { Text("اكتب يومك") }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                AppCard {
                    Text("التحليل", fontWeight = FontWeight.Bold)
                    Text("${entries.size} مذكرة • كل ما تكتب أكتر التحليل بيبقى أدق", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { run("events7") { DiaryAI.analyzeEvents(7).also { events = it } } }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                            if (busy == "events7") CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("أحداث الأسبوع")
                        }
                        FilledTonalButton(onClick = { run("events30") { DiaryAI.analyzeEvents(30).also { events = it } } }, enabled = busy == null, modifier = Modifier.weight(1f)) {
                            if (busy == "events30") CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("أحداث الشهر")
                        }
                    }
                    Button(onClick = { run("personality") { DiaryAI.analyzePersonality().also { personality = it } } }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                        if (busy == "personality") CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("تحليل الشخصية")
                    }
                    if (events.isNotBlank()) TextButton(onClick = { showReport = "events" to events }) { Text("آخر تحليل أحداث (${shortDate(DiaryAI.lastEventsAt)})") }
                    if (personality.isNotBlank()) TextButton(onClick = { showReport = "personality" to personality }) { Text("آخر تحليل شخصية (${shortDate(DiaryAI.lastPersonalityAt)})") }
                    Text(
                        "المذكرات متخزنة كتابة على تليفونك. بتتبعت للذكاء الاصطناعي بس لما تدوس تحليل. فعّل قفل البصمة من الإعدادات لو عايز خصوصية أكتر.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (entries.isEmpty()) item { EmptyState(Icons.Default.EditNote, "دوس \"اكتب يومك\" واحكي بصوتك، وهتتحفظ كتابة") }
            items(entries, key = { it.id }) { e ->
                AppCard(onClick = { viewing = e }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(e.mood.ifBlank { "📝" }, fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(dateStr(e.time) + " • " + timeStr(e.time), fontWeight = FontWeight.SemiBold)
                            Text(e.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (e.analysis.isNotBlank()) Icon(Icons.Default.AutoAwesome, "متحلل", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (creating) DiaryEditor(null) { saved -> creating = false; saved?.let { viewing = it } }
    editing?.let { e -> DiaryEditor(e) { saved -> editing = null; saved?.let { viewing = it } } }
    viewing?.let { e ->
        DiaryView(e, onEdit = { viewing = null; editing = e }, onClose = { viewing = null })
    }
    showReport?.let { (kind, text) ->
        Dialog(onDismissRequest = { showReport = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (kind == "personality") "قراءة في الشخصية" else "تحليل الأحداث", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showReport = null }) { Icon(Icons.Default.Close, "إغلاق") }
                    }
                    Text(text, style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp)
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun DiaryEditor(existing: DiaryEntry?, onDone: (DiaryEntry?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var mood by remember { mutableStateOf(existing?.mood ?: "") }
    var time by remember { mutableLongStateOf(existing?.time ?: System.currentTimeMillis()) }
    var tidying by remember { mutableStateOf(false) }
    val dictate = rememberVoiceInput { said -> text = if (text.isBlank()) said else text.trimEnd() + " " + said }

    Dialog(onDismissRequest = { onDone(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp).imePadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onDone(null) }) { Icon(Icons.Default.Close, "إلغاء") }
                    Text(if (existing == null) "يومي" else "تعديل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        if (text.isBlank()) toast(ctx, "احكي أو اكتب حاجة الأول") else scope.launch {
                            val e = DiaryEntry(id = existing?.id ?: 0, time = time, text = text.trim(), mood = mood, analysis = existing?.analysis ?: "")
                            val id = DiaryDb.dao.upsert(e)
                            onDone(e.copy(id = if (existing != null) existing.id else id))
                        }
                    }) { Text("حفظ") }
                }
                TextButton(onClick = { pickDateTime(ctx, time) { time = it } }) { Text(dateStr(time) + " • " + timeStr(time)) }
                ChipsRow(moods, mood.ifBlank { null }, { it }) { mood = if (mood == it) "" else it }
                OutlinedTextField(
                    text, { text = it }, placeholder = { Text("احكي يومك… إيه اللي حصل، حسيت بإيه، مين قابلت") },
                    modifier = Modifier.fillMaxWidth().weight(1f), textStyle = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(onClick = dictate, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Mic, "اتكلم", Modifier.size(28.dp)) }
                    Text("دوس واتكلم، وكل مرة الكلام بيتضاف للآخر", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        if (!Claude.hasKey) toast(ctx, "اربط ذكاء اصطناعي من الإعدادات") else if (text.isNotBlank()) {
                            tidying = true
                            scope.launch {
                                try { text = DiaryAI.tidy(text) } catch (e: Exception) { toast(ctx, e.message ?: "فشل") }
                                tidying = false
                            }
                        }
                    }, enabled = !tidying && text.isNotBlank()) {
                        if (tidying) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("رتّب الكلام")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiaryView(entry: DiaryEntry, onEdit: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var e by remember(entry.id) { mutableStateOf(entry) }
    var busy by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "إغلاق") }
                    Text("${e.mood} ${dateStr(e.time)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "تعديل") }
                    IconButton(onClick = { confirmDel = true }) { Icon(Icons.Default.Delete, "مسح") }
                }
                Text(timeStr(e.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(10.dp))
                Text(e.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 28.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    if (!Claude.hasKey) toast(ctx, "اربط ذكاء اصطناعي من الإعدادات") else {
                        busy = true
                        scope.launch {
                            try { DiaryAI.analyzeDay(e); DiaryDb.dao.get(e.id)?.let { e = it } } catch (ex: Exception) { toast(ctx, ex.message ?: "فشل") }
                            busy = false
                        }
                    }
                }, enabled = !busy) {
                    if (busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); Text("بيحلل…") }
                    else { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text(if (e.analysis.isBlank()) "حلل اليوم" else "حلل تاني") }
                }
                if (e.analysis.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    AppCard(color = MaterialTheme.colorScheme.primaryContainer) { Text(e.analysis, lineHeight = 26.sp) }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
    if (confirmDel) ConfirmDialog("مسح المذكرة؟", "مش هتقدر ترجعها", "امسح", { confirmDel = false }) {
        scope.launch { DiaryDb.dao.delete(e); onClose() }
    }
}
