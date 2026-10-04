package com.mohamed.safi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.core.content.FileProvider
import com.mohamed.safi.SafiApp
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.ai.ReceiptReader
import com.mohamed.safi.ai.ReceiptResult
import com.mohamed.safi.data.CURRENCIES
import com.mohamed.safi.data.Categorizer
import com.mohamed.safi.data.Cats
import com.mohamed.safi.data.Expense
import com.mohamed.safi.data.Fx
import com.mohamed.safi.data.fmt
import com.mohamed.safi.data.money
import com.mohamed.safi.location.LocationLogger
import com.mohamed.safi.location.LocationService
import com.mohamed.safi.notify.Brief
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

object UiBus {
    /** Text spoken on another screen, to be sent to the assistant. */
    val pendingVoice = MutableStateFlow<String?>(null)
    /** Route requested by a notification tap. */
    val pendingRoute = MutableStateFlow<String?>(null)
    /** Bank message(s) shared into the app from Messages. */
    val pendingShare = MutableStateFlow<String?>(null)
    /** Open the mic as soon as the assistant screen shows (from the launcher shortcut). */
    val listenNow = MutableStateFlow(false)
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun openFile(ctx: Context, path: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(path))
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Add / edit an expense (or income). [prefill] is used for new entries (e.g. from a receipt). */
@Composable
fun ExpenseEditor(existing: Expense?, prefill: Expense? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val base = existing ?: prefill
    var amount by remember { mutableStateOf(base?.amount?.let { fmt(it).replace(",", "") } ?: "") }
    var currency by remember { mutableStateOf(base?.currency ?: "AED") }
    var category by remember { mutableStateOf(base?.category ?: Cats.FOOD) }
    var merchant by remember { mutableStateOf(base?.merchant ?: "") }
    var note by remember { mutableStateOf(base?.note ?: "") }
    var method by remember { mutableStateOf(base?.method ?: "cash") }
    var time by remember { mutableStateOf(base?.time ?: System.currentTimeMillis()) }
    var income by remember { mutableStateOf(base?.isIncome ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) (if (income) "دخل جديد" else "مصروف جديد") else "تعديل") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !income, onClick = { income = false; if (category == Cats.INCOME) category = Cats.OTHER }, label = { Text("مصروف") })
                    FilterChip(selected = income, onClick = { income = true; category = Cats.INCOME }, label = { Text("دخل") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("المبلغ", amount, Modifier.weight(1.4f)) { amount = it }
                    ChoiceField("العملة", currency, CURRENCIES, Modifier.weight(1f)) { currency = it }
                }
                if (currency != "AED" && amount.toDoubleOrNull() != null) {
                    Text("= ${money(Fx.toAed(amount.toDouble(), currency))}", color = MaterialTheme.colorScheme.outline)
                }
                if (!income) ChoiceField("التصنيف", category, Cats.expense) { category = it }
                OutlinedTextField(merchant, { merchant = it }, label = { Text(if (income) "من" else "المكان / المحل") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!income) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = method == "cash", onClick = { method = "cash" }, label = { Text("كاش") }, leadingIcon = { Icon(Icons.Default.Payments, null) })
                    FilterChip(selected = method == "card", onClick = { method = "card" }, label = { Text("بطاقة") }, leadingIcon = { Icon(Icons.Default.CreditCard, null) })
                }
                DateField("الوقت", time) { time = it }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة") }, modifier = Modifier.fillMaxWidth())
                existing?.let { e ->
                    if (e.bank.isNotBlank() || e.placeName.isNotBlank()) {
                        Text(listOf(e.bank, e.placeName).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
                (existing?.receiptPath ?: prefill?.receiptPath)?.let { path ->
                    TextButton(onClick = { openFile(ctx, path) }) {
                        Icon(Icons.Default.Receipt, null); Spacer(Modifier.width(6.dp)); Text("شوف صورة الفاتورة")
                    }
                }
                if (existing != null) {
                    TextButton(onClick = { confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) {
                        Icon(Icons.Default.Delete, null); Spacer(Modifier.width(6.dp)); Text("امسح")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val a = amount.toDoubleOrNull() ?: 0.0
                val cat = if (income) Cats.INCOME else category
                if (a <= 0) toast(ctx, "اكتب المبلغ") else scope.launch {
                    val dao = SafiApp.db.dao()
                    if (existing != null) {
                        dao.updateExpense(
                            existing.copy(
                                amount = a, currency = currency, amountAed = Fx.toAed(a, currency), category = cat,
                                merchant = merchant.trim(), note = note.trim(), method = method, time = time, isIncome = income,
                            ),
                        )
                        if (!income && merchant.isNotBlank() && cat != existing.category) Categorizer.learn(merchant, cat)
                    } else {
                        val here = if (time > System.currentTimeMillis() - 3_600_000L) LocationService.currentLocation(ctx) else null
                        val place = LocationLogger.placeAt(time)
                        val e = Expense(
                            amount = a, currency = currency, amountAed = Fx.toAed(a, currency), category = cat,
                            merchant = merchant.trim(), note = note.trim(), method = method, time = time,
                            lat = here?.latitude ?: place?.lat, lng = here?.longitude ?: place?.lng,
                            placeName = place?.placeName ?: "",
                            source = prefill?.source ?: "manual", receiptPath = prefill?.receiptPath, isIncome = income,
                        )
                        dao.insertExpense(e)
                        if (!income && merchant.isNotBlank() && cat != Cats.OTHER) Categorizer.learn(merchant, cat)
                        if (!income) Brief.checkBudget(ctx, cat, e.amountAed)
                    }
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )

    if (confirmDelete && existing != null) {
        ConfirmDialog("مسح المصروف؟", money(existing.amount, existing.currency) + " — " + existing.category, "امسح", { confirmDelete = false }) {
            scope.launch {
                SafiApp.db.dao().deleteExpense(existing)
                onDismiss()
            }
        }
    }
}

class ReceiptController {
    var chooser by mutableStateOf(false)
    fun open() { chooser = true }
}

@Composable
fun rememberReceiptController() = remember { ReceiptController() }

/** Camera / gallery → Claude reads the receipt → editor pre-filled. */
@Composable
fun ReceiptHost(c: ReceiptController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ReceiptResult?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun process(uri: Uri) {
        if (!Claude.hasKey) {
            error = "قراءة الفواتير محتاجة تربط ذكاء اصطناعي من الإعدادات."
            return
        }
        loading = true
        scope.launch {
            try {
                result = ReceiptReader.read(ctx, uri)
            } catch (e: Exception) {
                error = e.message ?: "مقدرتش أقرا الفاتورة"
            } finally {
                loading = false
            }
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) process(u)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) process(uri)
    }

    if (c.chooser) {
        AlertDialog(
            onDismissRequest = { c.chooser = false },
            title = { Text("صوّر الفاتورة") },
            text = { Text("${com.mohamed.safi.AppName.v} هيقرا المحل والمبلغ والتصنيف لوحده، وانت تراجع قبل الحفظ.") },
            confirmButton = {
                TextButton(onClick = {
                    c.chooser = false
                    val f = File(ctx.cacheDir, "receipt_${System.currentTimeMillis()}.jpg")
                    val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    cameraUri = u
                    try { camera.launch(u) } catch (e: Exception) { toast(ctx, "الكاميرا مش متاحة") }
                }) { Icon(Icons.Default.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("الكاميرا") }
            },
            dismissButton = {
                TextButton(onClick = { c.chooser = false; gallery.launch("image/*") }) {
                    Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("من الصور")
                }
            },
        )
    }
    if (loading) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("بقرا الفاتورة…")
                }
            },
        )
    }
    error?.let { msg ->
        AlertDialog(
            onDismissRequest = { error = null },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("تمام") } },
        )
    }
    result?.let { r ->
        val prefill = Expense(
            amount = r.total, currency = r.currency, amountAed = Fx.toAed(r.total, r.currency),
            category = r.category, merchant = r.merchant, note = r.items.take(200),
            method = "card", time = r.time ?: System.currentTimeMillis(), source = "receipt", receiptPath = r.imagePath,
        )
        ExpenseEditor(existing = null, prefill = prefill) { result = null }
    }
}

@Composable
fun ExpenseRow(e: Expense, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CatBadge(e.category)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        e.merchant.ifBlank { e.category },
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(com.mohamed.safi.data.timeStr(e.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        if (e.merchant.isNotBlank()) Text(e.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                        when (e.source) {
                            "sms" -> Pill("بنك")
                            "voice" -> Pill("صوت", Gold)
                            "receipt" -> Pill("فاتورة", Color2)
                            "bill" -> Pill("التزام", Warn)
                        }
                        if (e.method == "cash" && !e.isIncome) Pill("كاش", Positive)
                    }
                    if (e.placeName.isNotBlank()) {
                        Text("📍 " + e.placeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        (if (e.isIncome) "+" else "") + fmt(e.amountAed),
                        fontWeight = FontWeight.Bold,
                        color = if (e.isIncome) Positive else MaterialTheme.colorScheme.onSurface,
                    )
                    if (e.currency != "AED") Text(money(e.amount, e.currency), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

val Color2 = androidx.compose.ui.graphics.Color(0xFF2E7DBA)
