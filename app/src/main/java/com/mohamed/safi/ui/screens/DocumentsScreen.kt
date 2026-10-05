package com.mohamed.safi.ui.screens

import android.app.Activity
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.mohamed.safi.data.*
import com.mohamed.safi.docs.*
import com.mohamed.safi.extra.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val typeIcons = mapOf(
    "badge" to Icons.Default.Badge, "home" to Icons.Default.Home, "flight" to Icons.Default.Flight, "card" to Icons.Default.CreditCard,
    "car" to Icons.Default.DirectionsCar, "shield" to Icons.Default.Shield, "health" to Icons.Default.LocalHospital, "key" to Icons.Default.Key,
    "work" to Icons.Default.Work, "school" to Icons.Default.School, "folder" to Icons.Default.Folder, "receipt" to Icons.Default.Receipt,
)

private fun pagesOf(d: Doc, extra: List<DocPage>): List<String> =
    listOfNotNull(d.photoPath) + extra.filter { it.docId == d.id }.sortedBy { it.idx }.map { it.path }

@Composable
fun DocumentsScreen(onBack: () -> Unit) {
    val docs by remember { ExtraDb.dao.docs() }.collectAsState(emptyList())
    val types by remember { DocsDb.dao.types() }.collectAsState(emptyList())
    val metas by remember { DocsDb.dao.metas() }.collectAsState(emptyList())
    val pages by remember { DocsDb.dao.allPages() }.collectAsState(emptyList())
    var filter by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Doc?>(null) }
    var viewing by remember { mutableStateOf<Long?>(null) }
    var editTypes by remember { mutableStateOf(false) }

    val typeOf: (Doc) -> Long? = { d -> metas.firstOrNull { it.docId == d.id }?.typeId ?: types.firstOrNull { it.name == d.title }?.id }

    val v = viewing?.let { id -> docs.firstOrNull { it.id == id } }
    if (v != null) { DocViewer(v, pagesOf(v, pages), onEdit = { editing = v }) { viewing = null }; editing?.let { d -> DocDialog(d, types, typeOf(d)) { editing = null } }; return }

    val shown = docs.filter { filter == null || typeOf(it) == filter }
    val soon = docs.filter { d -> d.expiry?.let { daysUntil(it) <= d.remindDays } == true }
    ScreenScaffold(
        "المستندات", onBack = onBack,
        actions = { IconButton(onClick = { editTypes = true }) { Icon(Icons.Default.Tune, "الأنواع") } },
        fab = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("مستند") }) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (soon.isNotEmpty()) item {
                AppCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text("محتاج تجديد", fontWeight = FontWeight.Bold)
                    soon.forEach { d ->
                        val days = daysUntil(d.expiry!!)
                        Text("• ${d.title}" + (if (d.owner.isNotBlank()) " (${d.owner})" else "") + if (days < 0) " — منتهي" else " — فاضل $days يوم",
                            style = MaterialTheme.typography.bodyMedium, color = if (days < 0) Danger else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { FilterChip(filter == null, { filter = null }, label = { Text("الكل (${docs.size})") }) }
                    items(types, key = { it.id }) { t ->
                        val n = docs.count { typeOf(it) == t.id }
                        FilterChip(filter == t.id, { filter = if (filter == t.id) null else t.id }, label = { Text(if (n > 0) "${t.name} ($n)" else t.name) },
                            leadingIcon = { Icon(typeIcons[t.icon] ?: Icons.Default.Folder, null, Modifier.size(16.dp)) })
                    }
                }
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Default.DocumentScanner, "ضيف الهوية والإقامة والجواز والرخصة. تقدر تصوّرها سكانر وتنزلها PDF.") }
            items(shown, key = { it.id }) { d ->
                val ps = pagesOf(d, pages)
                val days = d.expiry?.let { daysUntil(it) }
                val color = when { days == null -> MaterialTheme.colorScheme.outline; days < 0 -> Danger; days <= d.remindDays -> Warn; else -> Positive }
                val t = types.firstOrNull { it.id == typeOf(d) }
                AppCard(onClick = { viewing = d.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ps.isNotEmpty()) AsyncImage(File(ps.first()), null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 64.dp, height = 46.dp).clip(RoundedCornerShape(8.dp)))
                        else CatBadge(d.title, 46, typeIcons[t?.icon] ?: Icons.Default.Description, color)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.title + if (d.owner.isNotBlank()) " — ${d.owner}" else "", fontWeight = FontWeight.SemiBold)
                            Text(listOfNotNull(t?.name?.takeIf { it != d.title }, d.number.takeIf { it.isNotBlank() }, if (ps.isNotEmpty()) "${ps.size} صفحة" else null).joinToString(" • "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            d.expiry?.let {
                                Text("ينتهي ${shortDate(it)} • " + when {
                                    days!! < 0 -> "منتهي من ${-days} يوم"; days == 0L -> "النهارده"; days < 60 -> "فاضل $days يوم"; else -> "فاضل ${days / 30} شهر"
                                }, style = MaterialTheme.typography.bodySmall, color = color)
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) DocDialog(null, types, filter) { saved -> adding = false; if (saved != null) viewing = saved }
    editing?.let { d -> DocDialog(d, types, typeOf(d)) { editing = null } }
    if (editTypes) TypesDialog(types) { editTypes = false }
}

/** Add / edit a document's details. Returns the saved id (or null when cancelled). */
@Composable
private fun DocDialog(existing: Doc?, types: List<DocType>, typeId: Long?, onDone: (Long?) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(typeId) }
    var title by remember { mutableStateOf(existing?.title ?: types.firstOrNull { it.id == typeId }?.name ?: "") }
    var owner by remember { mutableStateOf(existing?.owner ?: "") }
    var number by remember { mutableStateOf(existing?.number ?: "") }
    var expiry by remember { mutableStateOf(existing?.expiry) }
    var remind by remember { mutableStateOf((existing?.remindDays ?: 30).toString()) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (existing == null) "مستند جديد" else "تعديل البيانات") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("النوع", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(types, key = { it.id }) { t ->
                        FilterChip(type == t.id, {
                            val old = types.firstOrNull { it.id == type }?.name
                            type = t.id
                            if (title.isBlank() || title == old) title = t.name
                        }, label = { Text(t.name) })
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("اسم المستند") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(owner, { owner = it }, label = { Text("بتاع مين (انا، مراتي، الأولاد…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(number, { number = it }, label = { Text("الرقم (اختياري)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DateField("تاريخ الانتهاء", expiry, withTime = false) { expiry = it }
                NumberField("فكرني قبلها بكام يوم", remind) { remind = it }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") }, modifier = Modifier.fillMaxWidth())
                if (existing != null) TextButton(onClick = { confirmDel = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) {
                    Icon(Icons.Default.Delete, null); Text(" امسح المستند")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isBlank()) { toast(ctx, "اكتب اسم المستند"); return@TextButton }
                scope.launch {
                    val id = ExtraDb.dao.upsertDoc(
                        (existing ?: Doc(title = title)).copy(
                            title = title.trim(), owner = owner.trim(), number = number.trim(),
                            expiry = expiry?.toLocalDate()?.millisAt(9), remindDays = remind.toIntOrNull() ?: 30, note = note.trim(),
                        ),
                    )
                    val realId = existing?.id ?: id
                    DocsDb.dao.upsertMeta(DocMeta(docId = realId, typeId = type))
                    onDone(realId)
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("إلغاء") } },
    )
    if (confirmDel && existing != null) ConfirmDialog("مسح ${existing.title}؟", "هيتمسح بكل صفحاته.", "امسح", { confirmDel = false }) {
        scope.launch {
            withContext(Dispatchers.IO) {
                DocFiles.delete(existing.photoPath)
                DocsDb.dao.pagesOf(existing.id).forEach { DocFiles.delete(it.path) }
            }
            DocsDb.dao.forgetDoc(existing.id)
            ExtraDb.dao.deleteDoc(existing)
            onDone(null)
        }
    }
}

/** Full-screen pages viewer: swipe, zoom, add (scanner / camera / gallery), delete, export PDF. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocViewer(d: Doc, pages: List<String>, onEdit: () -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { pages.size.coerceAtLeast(1) }
    var camFile by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelPage by remember { mutableStateOf(false) }

    fun savePages(list: List<String>) = scope.launch {
        ExtraDb.dao.upsertDoc(d.copy(photoPath = list.firstOrNull()))
        DocsDb.dao.replaceExtraPages(d.id, list.drop(1))
    }
    fun addPaths(new: List<String>) { if (new.isNotEmpty()) { savePages(pages + new); toast(ctx, "اتضاف ${new.size} صفحة") } }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = camFile
        if (ok && f != null && f.length() > 0) addPaths(listOf(f.absolutePath)) else f?.delete()
    }
    fun openCamera() {
        val f = DocFiles.newFile(ctx); camFile = f
        runCatching { camera.launch(DocFiles.uriFor(ctx, f)) }.onFailure { toast(ctx, "الكاميرا مش متاحة") }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch { addPaths(withContext(Dispatchers.IO) { uris.mapNotNull { DocFiles.copyIn(ctx, it) } }) }
    }
    val scanLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val res = GmsDocumentScanningResult.fromActivityResultIntent(r.data)
            val uris = res?.pages?.map { it.imageUri } ?: emptyList()
            scope.launch { addPaths(withContext(Dispatchers.IO) { uris.mapNotNull { DocFiles.copyIn(ctx, it) } }) }
        }
    }
    fun openScanner() {
        val act = ctx as? ComponentActivity ?: run { openCamera(); return }
        val opts = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true).setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        GmsDocumentScanning.getClient(opts).getStartScanIntent(act)
            .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { toast(ctx, "السكانر مش متاح على الموبايل ده، هفتح الكاميرا"); openCamera() }
    }
    fun pdf(share: Boolean) {
        if (pages.isEmpty()) { toast(ctx, "ضيف صفحة الأول"); return }
        busy = true
        scope.launch {
            val f = withContext(Dispatchers.IO) { DocPdf.build(ctx, d.title + if (d.owner.isNotBlank()) " - ${d.owner}" else "", pages) }
            busy = false
            if (f == null) { toast(ctx, "مقدرتش أعمل PDF"); return@launch }
            if (share) runCatching { ctx.startActivity(DocPdf.shareIntent(ctx, f)) }
            else {
                val where = withContext(Dispatchers.IO) { DocPdf.saveToDownloads(ctx, f) }
                toast(ctx, if (where != null) "اتحفظ في $where" else "مقدرتش أحفظ")
            }
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text(d.title, color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.Close, "قفل", tint = Color.White) } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "تعديل", tint = Color.White) }
                    IconButton(onClick = { pdf(true) }) { Icon(Icons.Default.Share, "شارك", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
            )
        },
        bottomBar = {
            Column(Modifier.background(Color.Black).navigationBarsPadding().padding(12.dp)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { openScanner() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.DocumentScanner, null); Text(" سكانر") }
                    FilledTonalButton(onClick = { openCamera() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.CameraAlt, null); Text(" كاميرا") }
                    FilledTonalButton(onClick = { gallery.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PhotoLibrary, null); Text(" صور") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { pdf(false) }, enabled = pages.isNotEmpty() && !busy, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PictureAsPdf, null, tint = Color.White); Text(" نزّل PDF", color = Color.White)
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (pages.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DocumentScanner, null, tint = GoldSoft, modifier = Modifier.size(64.dp))
                    Text("مفيش صفحات لسه. دوس «سكانر» وحط المستند قدام الكاميرا، وهو هيقصه ويعدله لوحده.", color = Color.White)
                }
            } else {
                HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                    var scale by remember { mutableFloatStateOf(1f) }
                    var off by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                    AsyncImage(
                        File(pages[i]), null, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                            .pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); off = if (scale == 1f) androidx.compose.ui.geometry.Offset.Zero else off + pan } }
                            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = off.x, translationY = off.y),
                    )
                }
                Row(Modifier.align(Alignment.TopCenter).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50)) {
                        Text("${pager.currentPage + 1} / ${pages.size}", color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    if (pager.currentPage > 0) IconButton(onClick = {
                        val i = pager.currentPage
                        savePages(pages.toMutableList().also { val x = it.removeAt(i); it.add(i - 1, x) })
                    }) { Icon(Icons.Default.ArrowForward, "قدّم الصفحة", tint = Color.White) }
                    IconButton(onClick = { confirmDelPage = true }) { Icon(Icons.Default.Delete, "امسح الصفحة", tint = Color.White) }
                }
            }
        }
    }
    if (confirmDelPage) ConfirmDialog("تمسح الصفحة دي؟", "", "امسح", { confirmDelPage = false }) {
        val i = pager.currentPage.coerceIn(0, pages.lastIndex)
        DocFiles.delete(pages[i])
        savePages(pages.filterIndexed { k, _ -> k != i })
    }
}

/** Rename, add, delete document types. */
@Composable
private fun TypesDialog(types: List<DocType>, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var newIcon by remember { mutableStateOf("folder") }
    var renaming by remember { mutableStateOf<DocType?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("أنواع المستندات") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                types.forEach { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(typeIcons[t.icon] ?: Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(t.name, Modifier.weight(1f))
                        IconButton(onClick = { renaming = t }) { Icon(Icons.Default.Edit, "غيّر الاسم") }
                        IconButton(onClick = { scope.launch { DocsDb.dao.deleteType(t) } }) { Icon(Icons.Default.Delete, "امسح", tint = Danger) }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                OutlinedTextField(newName, { newName = it }, label = { Text("نوع جديد") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(typeIcons.keys.toList()) { k ->
                        FilterChip(newIcon == k, { newIcon = k }, label = { Icon(typeIcons[k]!!, null, Modifier.size(18.dp)) })
                    }
                }
                Button(onClick = {
                    val n = newName.trim()
                    if (n.isNotEmpty()) scope.launch { DocsDb.dao.upsertType(DocType(name = n, icon = newIcon, sort = DocsDb.dao.maxSort() + 1)); newName = "" }
                }, enabled = newName.isNotBlank()) { Text("ضيف") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تمام") } },
    )
    renaming?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("غيّر الاسم") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { if (name.isNotBlank()) scope.launch { DocsDb.dao.upsertType(t.copy(name = name.trim())) }; renaming = null }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("إلغاء") } },
        )
    }
}
