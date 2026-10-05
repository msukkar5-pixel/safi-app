package com.mohamed.safi.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.mohamed.safi.ui.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import com.mohamed.safi.data.*
import com.mohamed.safi.extra.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// ======================================================================= Documents

private val docPresets = listOf("الهوية الإماراتية", "الإقامة", "الجواز", "رخصة السواقة", "ملكية العربية", "تأمين العربية", "التأمين الصحي", "عقد الإيجار (إيجاري)", "بطاقة العمل")

@Composable
fun DocumentsScreen(onBack: () -> Unit) {
    val docs by ExtraDb.dao.docs().collectAsState(emptyList())
    var editing by remember { mutableStateOf<Doc?>(null) }
    var adding by remember { mutableStateOf<String?>(null) }
    val ctx = LocalContext.current
    ScreenScaffold(
        "المستندات", onBack = onBack,
        fab = { ExtendedFloatingActionButton(onClick = { adding = "" }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("مستند") }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("هفكرك قبل ما أي مستند ينتهي. كل حاجة متخزنة على تليفونك بس.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(6.dp))
                ChipsRow(docPresets.filter { p -> docs.none { it.title == p } }, null, { it }) { adding = it }
            }
            if (docs.isEmpty()) item { EmptyState(Icons.Default.Badge, "ضيف الهوية والإقامة والجواز والرخصة") }
            items(docs, key = { it.id }) { d ->
                val days = d.expiry?.let { daysUntil(it) }
                val color = when {
                    days == null -> MaterialTheme.colorScheme.outline
                    days < 0 -> Danger
                    days <= d.remindDays -> Warn
                    else -> Positive
                }
                AppCard(onClick = { editing = d }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CatBadge(d.title, 40, Icons.Default.Badge, color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.title + if (d.owner.isNotBlank()) " — ${d.owner}" else "", fontWeight = FontWeight.SemiBold)
                            if (d.number.isNotBlank()) Text(d.number, style = MaterialTheme.typography.bodySmall)
                            d.expiry?.let {
                                Text(
                                    "ينتهي ${shortDate(it)} • " + when {
                                        days!! < 0 -> "منتهي من ${-days} يوم"
                                        days == 0L -> "النهارده"
                                        days < 60 -> "فاضل $days يوم"
                                        else -> "فاضل ${days / 30} شهر"
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = color,
                                )
                            }
                        }
                        d.photoPath?.let { p -> IconButton(onClick = { openFile(ctx, p) }) { Icon(Icons.Default.Image, "الصورة") } }
                    }
                }
            }
        }
    }
    adding?.let { t -> DocDialog(null, t) { adding = null } }
    editing?.let { d -> DocDialog(d, d.title) { editing = null } }
}

@Composable
private fun DocDialog(existing: Doc?, preset: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(existing?.title ?: preset) }
    var owner by remember { mutableStateOf(existing?.owner ?: "") }
    var number by remember { mutableStateOf(existing?.number ?: "") }
    var expiry by remember { mutableStateOf(existing?.expiry) }
    var remind by remember { mutableStateOf((existing?.remindDays ?: 30).toString()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var photo by remember { mutableStateOf(existing?.photoPath) }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    var camFile by remember { mutableStateOf<File?>(null) }
    var confirmDel by remember { mutableStateOf(false) }

    fun keep(src: Uri) {
        val dir = File(ctx.filesDir, "docs").apply { mkdirs() }
        val f = File(dir, "doc_${System.currentTimeMillis()}.jpg")
        runCatching { ctx.contentResolver.openInputStream(src)?.use { i -> f.outputStream().use { i.copyTo(it) } }; photo = f.absolutePath }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) camFile?.let { photo = it.absolutePath } }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(::keep) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "مستند جديد" else existing.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("المستند") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(owner, { owner = it }, label = { Text("بتاع مين (انا، مراتي، الأولاد…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(number, { number = it }, label = { Text("الرقم (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DateField("تاريخ الانتهاء", expiry, withTime = false) { expiry = it }
                NumberField("فكرني قبلها بكام يوم", remind) { remind = it }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val dir = File(ctx.filesDir, "docs").apply { mkdirs() }
                        val f = File(dir, "doc_${System.currentTimeMillis()}.jpg")
                        camFile = f
                        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        camUri = u
                        runCatching { camera.launch(u) }
                    }) { Icon(Icons.Default.CameraAlt, null); Text(" صوّر") }
                    OutlinedButton(onClick = { gallery.launch("image/*") }) { Icon(Icons.Default.PhotoLibrary, null); Text(" من الصور") }
                }
                photo?.let { p ->
                    TextButton(onClick = { openFile(ctx, p) }) { Icon(Icons.Default.Image, null); Text(" شوف الصورة") }
                }
                if (existing != null) TextButton(onClick = { confirmDel = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) {
                    Icon(Icons.Default.Delete, null); Text("امسح")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) toast(ctx, "اكتب اسم المستند") else scope.launch {
                    ExtraDb.dao.upsertDoc(
                        Doc(
                            id = existing?.id ?: 0, title = title.trim(), owner = owner.trim(), number = number.trim(),
                            expiry = expiry?.toLocalDate()?.millisAt(9), remindDays = remind.toIntOrNull() ?: 30, photoPath = photo, note = note.trim(),
                        ),
                    )
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDel && existing != null) {
        ConfirmDialog("مسح ${existing.title}؟", "", "امسح", { confirmDel = false }) {
            scope.launch {
                existing.photoPath?.let { File(it).delete() }
                ExtraDb.dao.deleteDoc(existing); onDismiss()
            }
        }
    }
}

// ======================================================================= Savings goals

