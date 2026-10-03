package com.mohamed.safi.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.mohamed.safi.ai.Claude
import com.mohamed.safi.data.*
import com.mohamed.safi.fitness.*
import com.mohamed.safi.ui.*
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private val meals = listOf("فطار", "سناك", "قبل التمرين", "بعد التمرين", "غدا", "عشا")

@Composable
fun FoodTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now(zone)
    val (from, to) = remember { dayRange(today) }
    val foods by Fit.dao.foodsBetween(from, to).collectAsState(emptyList())
    val supps by Fit.dao.supplements().collectAsState(emptyList())
    val weights by Fit.dao.weights().collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<FoodEntry?>(null) }
    var editSupp by remember { mutableStateOf<Supplement?>(null) }
    var addSupp by remember { mutableStateOf(false) }
    var planBusy by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf(Fit.prefs.dietPlan) }
    var showPlan by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<String?>(null) }
    var reviewBusy by remember { mutableStateOf(false) }
    val t = Fit.targets(weights.firstOrNull()?.kg)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AppCard {
                Text("النهارده", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                val kcal = foods.sumOf { it.kcal }; val p = foods.sumOf { it.protein }; val c = foods.sumOf { it.carbs }; val f = foods.sumOf { it.fat }
                BarRow("سعرات", "${kcal.toInt()} / ${t?.kcal ?: "-"}", t?.let { (kcal / it.kcal).toFloat() } ?: 0f, Warn)
                BarRow("بروتين", "${p.toInt()} / ${t?.protein ?: "-"} g", t?.let { (p / it.protein).toFloat() } ?: 0f, Brand)
                BarRow("كارب", "${c.toInt()} / ${t?.carbs ?: "-"} g", t?.let { (c / it.carbs).toFloat() } ?: 0f, Color2)
                BarRow("دهون", "${f.toInt()} / ${t?.fat ?: "-"} g", t?.let { (f / it.fat).toFloat() } ?: 0f, Gold)
                if (t != null && kcal < t.kcal) Text("فاضلك ${(t.kcal - kcal).toInt()} سعر و ${(t.protein - p).coerceAtLeast(0.0).toInt()} جرام بروتين", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Text("سجّل أكل") }
            }
        }
        meals.forEach { m ->
            val list = foods.filter { it.meal == m }
            if (list.isNotEmpty()) {
                item { Text(m, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
                items(list, key = { it.id }) { f -> FoodRow(f) { del = f } }
            }
        }
        val other = foods.filter { it.meal !in meals }
        if (other.isNotEmpty()) {
            item { Text("أكل تاني", fontWeight = FontWeight.Bold) }
            items(other, key = { it.id }) { f -> FoodRow(f) { del = f } }
        }

        // Supplements
        item {
            SectionTitle("المكملات") { TextButton(onClick = { addSupp = true }) { Icon(Icons.Default.Add, null); Text("ضيف") } }
        }
        if (supps.isEmpty()) item { Text("ضيف المكملات اللي بتاخدها وأنا أفكرك بمواعيدها.", color = MaterialTheme.colorScheme.outline) }
        items(supps, key = { "s" + it.id }) { s ->
            AppCard(onClick = { editSupp = s }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CatBadge(s.name, 36, Icons.Default.Medication, Color2)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name + if (s.dose.isNotBlank()) " — ${s.dose}" else "", fontWeight = FontWeight.SemiBold)
                        Text(
                            (if (s.times.isNotBlank()) "⏰ ${s.times}" else "من غير تذكير") + if (s.note.isNotBlank()) " • ${s.note}" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    if (!s.active) Pill("متوقف", MaterialTheme.colorScheme.outline)
                }
            }
        }
        item {
            OutlinedButton(onClick = {
                if (!Claude.hasKey) toast(ctx, "محتاج مفتاح Claude") else {
                    reviewBusy = true
                    scope.launch {
                        review = try { Coach.reviewSupplements() } catch (e: Exception) { "⚠️ " + (e.message ?: "") }
                        reviewBusy = false
                    }
                }
            }, enabled = !reviewBusy, modifier = Modifier.fillMaxWidth()) {
                if (reviewBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("راجع مكملاتي وانصحني")
            }
        }
        review?.let { r -> item { AppCard(color = MaterialTheme.colorScheme.primaryContainer) { Text(r) } } }

        // Diet plan
        item { SectionTitle("النظام الغذائي") }
        item {
            AppCard {
                Text(
                    if (plan.isBlank()) "Claude هيعملك نظام كامل على حسب احتياجك وأكلك اللي بتحبه ومكملاتك."
                    else "اتعمل ${shortDate(Fit.prefs.dietPlanDate)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        if (!Claude.hasKey) toast(ctx, "محتاج مفتاح Claude")
                        else if (Fit.prefs.heightCm <= 0) toast(ctx, "كمّل ملفك الأول (زرار الشخص فوق)")
                        else {
                            planBusy = true
                            scope.launch {
                                try { plan = Coach.dietPlan(); showPlan = true } catch (e: Exception) { toast(ctx, e.message ?: "") }
                                planBusy = false
                            }
                        }
                    }, enabled = !planBusy) {
                        if (planBusy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)); Text("بيجهز…") }
                        else Text(if (plan.isBlank()) "اعملي نظام" else "نظام جديد")
                    }
                    if (plan.isNotBlank()) OutlinedButton(onClick = { showPlan = !showPlan }) { Text(if (showPlan) "اخفي" else "اعرض") }
                }
                if (showPlan && plan.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(plan)
                }
            }
        }
    }

    if (adding) AddFoodDialog { adding = false }
    del?.let { f ->
        ConfirmDialog("مسح؟", "${f.text} — ${f.kcal.toInt()} سعر", "امسح", { del = null }) { scope.launch { Fit.dao.deleteFood(f) } }
    }
    if (addSupp) SuppDialog(null) { addSupp = false }
    editSupp?.let { s -> SuppDialog(s) { editSupp = null } }
}

@Composable
private fun FoodRow(f: FoodEntry, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(f.text, maxLines = 3)
                Text(timeStr(f.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${f.kcal.toInt()} سعر", fontWeight = FontWeight.Bold)
                Text("P ${f.protein.toInt()} • C ${f.carbs.toInt()} • F ${f.fat.toInt()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

private fun defaultMeal(): String {
    val h = java.time.LocalTime.now().hour
    return when (h) {
        in 4..10 -> "فطار"
        in 11..15 -> "غدا"
        in 19..23 -> "عشا"
        else -> "سناك"
    }
}

@Composable
private fun AddFoodDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var meal by remember { mutableStateOf(defaultMeal()) }
    var text by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun fill(e: Coach.FoodEstimate) {
        if (e.text.isNotBlank()) text = e.text
        kcal = e.kcal.toInt().toString(); protein = e.protein.toInt().toString()
        carbs = e.carbs.toInt().toString(); fat = e.fat.toInt().toString()
    }
    fun photo(uri: Uri) {
        if (!Claude.hasKey) { toast(ctx, "محتاج مفتاح Claude"); return }
        busy = true
        scope.launch {
            try { fill(Coach.estimateFoodPhoto(ctx, uri, text)) } catch (e: Exception) { toast(ctx, e.message ?: "") }
            busy = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> cameraUri?.let { if (ok) photo(it) } }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(::photo) }
    val voice = rememberVoiceInput { text = it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("سجّل أكل") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipsRow(meals, meal, { it }) { meal = it }
                OutlinedTextField(
                    text, { text = it }, label = { Text("أكلت إيه؟ (مثلاً: 200 جرام صدور فراخ ورز)") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { IconButton(onClick = voice) { Icon(Icons.Default.Mic, "بالصوت") } },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = {
                        if (!Claude.hasKey) toast(ctx, "محتاج مفتاح Claude") else if (text.isBlank()) toast(ctx, "اكتب أكلت إيه") else {
                            busy = true
                            scope.launch {
                                try { fill(Coach.estimateFood(text)) } catch (e: Exception) { toast(ctx, e.message ?: "") }
                                busy = false
                            }
                        }
                    }, enabled = !busy) { Text("احسبها") }
                    OutlinedButton(onClick = {
                        val f = File(ctx.cacheDir, "food_${System.currentTimeMillis()}.jpg")
                        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        cameraUri = u
                        runCatching { camera.launch(u) }
                    }, enabled = !busy) { Icon(Icons.Default.CameraAlt, "صوّر") }
                    OutlinedButton(onClick = { gallery.launch("image/*") }, enabled = !busy) { Icon(Icons.Default.PhotoLibrary, "صورة") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("سعرات", kcal, Modifier.weight(1f)) { kcal = it }
                    NumberField("بروتين", protein, Modifier.weight(1f)) { protein = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("كارب", carbs, Modifier.weight(1f)) { carbs = it }
                    NumberField("دهون", fat, Modifier.weight(1f)) { fat = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isBlank()) toast(ctx, "اكتب أكلت إيه") else scope.launch {
                    Fit.dao.insertFood(
                        FoodEntry(
                            meal = meal, text = text.trim(), kcal = kcal.toDoubleOrNull() ?: 0.0, protein = protein.toDoubleOrNull() ?: 0.0,
                            carbs = carbs.toDoubleOrNull() ?: 0.0, fat = fat.toDoubleOrNull() ?: 0.0,
                        ),
                    )
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun SuppDialog(existing: Supplement?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dose by remember { mutableStateOf(existing?.dose ?: "") }
    var times by remember { mutableStateOf(existing?.times ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var confirmDel by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "مكمل جديد" else existing.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("الاسم (كرياتين، واي بروتين، أوميجا 3…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(dose, { dose = it }, label = { Text("الجرعة (5 جم، كبسولة…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(times, { times = it }, label = { Text("المواعيد (08:00, 21:00)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row {
                    TextButton(onClick = {
                        pickTime(ctx, System.currentTimeMillis()) { h, m ->
                            val t = "%02d:%02d".format(h, m)
                            times = (Supps.parseTimes(times).map { it.toString() } + t).distinct().sorted().joinToString(", ")
                        }
                    }) { Icon(Icons.Default.Schedule, null); Text("ضيف ميعاد") }
                    if (times.isNotBlank()) TextButton(onClick = { times = "" }) { Text("امسح المواعيد") }
                }
                OutlinedTextField(note, { note = it }, label = { Text("ملاحظة (مع الأكل، بعد التمرين…)") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("شغّال", Modifier.weight(1f)); Switch(active, { active = it })
                }
                if (existing != null) TextButton(onClick = { confirmDel = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) {
                    Icon(Icons.Default.Delete, null); Text("امسح")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) toast(ctx, "اكتب الاسم") else scope.launch {
                    Supps.save(ctx, Supplement(id = existing?.id ?: 0, name = name.trim(), dose = dose.trim(), times = times.trim(), note = note.trim(), active = active))
                    onDismiss()
                }
            }) { Text("حفظ", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
    if (confirmDel && existing != null) {
        ConfirmDialog("مسح ${existing.name}؟", "والتذكيرات بتاعته", "امسح", { confirmDel = false }) {
            scope.launch { Supps.delete(ctx, existing); onDismiss() }
        }
    }
}
