package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.mohamed.safi.faith.Books
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.compose.material3.Text as RawText

/** Classical Arabic dictionaries in the order they are searched (simplest first). */
private val dictionaries = listOf("d_mukhtar", "d_misbah", "d_qamus", "d_maqayis", "d_asas", "d_sihah", "d_lisan", "d_taj")

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }; addOnFailureListener { c.resumeWithException(it) }
}

/** English ↔ Arabic translation on the phone (ML Kit, offline after a one-time download). */
private suspend fun translate(text: String, toArabic: Boolean): String {
    val opts = TranslatorOptions.Builder()
        .setSourceLanguage(if (toArabic) TranslateLanguage.ENGLISH else TranslateLanguage.ARABIC)
        .setTargetLanguage(if (toArabic) TranslateLanguage.ARABIC else TranslateLanguage.ENGLISH).build()
    val t = Translation.getClient(opts)
    try {
        t.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        return t.translate(text).await()
    } finally { t.close() }
}

/** Dictionary: English ↔ Arabic translation, and an Arabic word's meaning from the classical dictionaries. */
@Composable
fun DictionaryScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var translation by remember { mutableStateOf<String?>(null) }
    var trError by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<Triple<String, String, String>>>(emptyList()) } // book id, book title, snippet
    val ready = dictionaries.filter { Books.isReady(it) }
    val catalog = remember { Books.catalog(ctx).associateBy { it.id } }

    fun lookup() {
        val w = q.trim()
        if (w.isEmpty()) return
        val arabic = w.any { it in '؀'..'ۿ' }
        busy = true; translation = null; trError = false; hits = emptyList()
        scope.launch {
            translation = runCatching { translate(w, toArabic = !arabic) }.onFailure { trError = true }.getOrNull()
            if (arabic) {
                val out = mutableListOf<Triple<String, String, String>>()
                for (id in ready) {
                    val found = runCatching { Books.data(id).search(w, 3) }.getOrDefault(emptyList())
                    found.forEach { h -> out += Triple(id, catalog[id]?.title ?: id, h.snippet) }
                }
                hits = out
            }
            busy = false
        }
    }

    ScreenScaffold("القاموس", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(q, { q = it.take(60) }, label = { Text("اكتب كلمة عربي أو English") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { IconButton(onClick = { lookup() }) { Icon(Icons.Default.Search, null) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { lookup() }))
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            translation?.let { t ->
                item {
                    GoldCard {
                        Text("الترجمة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        RawText(t, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (trError) item { Text("الترجمة محتاجة إنترنت أول مرة بس عشان تنزل (حوالي ٣٠ ميجا)، وبعدها بتشتغل من غير نت.", style = MaterialTheme.typography.bodySmall, color = Danger) }
            if (hits.isNotEmpty()) item { SectionTitle("المعنى في المعاجم") }
            items(hits) { (id, title, snip) ->
                AppCard(onClick = { UiBus.pendingBook.value = id to q.trim(); open("book/$id") }) {
                    RawText(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    RawText(snip, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!busy && hits.isEmpty() && translation != null && q.any { it in '؀'..'ۿ' } && ready.isNotEmpty())
                item { Text("مالقيتش الكلمة بنصها في المعاجم. جرّب جذرها (مثلاً «كتب» بدل «مكتبة»).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            item {
                AppCard {
                    Text("المعاجم العربية", fontWeight = FontWeight.Bold)
                    Text(if (ready.isEmpty()) "نزّل معجم واحد على الأقل عشان تشوف معاني الكلمات العربية من غير نت. «مختار الصحاح» أصغرها وأسهلها."
                        else "البحث شغال في ${ready.size} معجم متنزّل.", style = MaterialTheme.typography.bodySmall)
                    dictionaries.forEach { id ->
                        val m = catalog[id] ?: return@forEach
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (Books.isReady(id)) Icons.Default.CheckCircle else Icons.Default.Download, null,
                                tint = if (Books.isReady(id)) Positive else MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { open("book/$id") }) { RawText("${m.title} — ${m.author}") }
                        }
                    }
                    if (catalog.isEmpty()) Text("قائمة الكتب لسه بتتحمّل…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            item { Text("الترجمة الإنجليزي ↔ العربي بتتم على موبايلك (Google ML Kit) ومجانية، وهي ترجمة سريعة مش شرح قاموس كامل.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
        }
    }
}
