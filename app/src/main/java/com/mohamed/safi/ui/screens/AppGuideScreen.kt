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
import com.mohamed.safi.ui.*
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.material3.Text as RawText

/** How to use every part of the app (assets/guide.json, ar/en/ur), searchable, each section with a button to open it. */
@Composable
fun AppGuideScreen(onBack: () -> Unit, open: (String) -> Unit) {
    val ctx = LocalContext.current
    val sections = remember {
        runCatching { JSONArray(ctx.assets.open("guide.json").bufferedReader().use { it.readText() }) }.getOrDefault(JSONArray())
            .let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    }
    var q by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf<String?>(null) }
    fun tips(o: JSONObject) = o.optJSONArray("tips")?.let { a -> (0 until a.length()).map { KidData.t(a.getJSONObject(it)) } }.orEmpty()
    val shown = sections.filter { s -> q.isBlank() || KidData.t(s.optJSONObject("title")).contains(q, true) || tips(s).any { it.contains(q, true) } }
    ScreenScaffold("دليل التطبيق", onBack = onBack) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                GoldCard {
                    Text("أهلاً بيك في ${com.mohamed.safi.AppName.v} 👋", fontFamily = Amiri, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    Text("هنا شرح مختصر لكل قسم. اضغط على أي قسم تشوف إزاي تستخدمه، أو افتحه على طول.", style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                OutlinedTextField(q, { q = it }, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("دوّر في الدليل…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            if (shown.isEmpty()) item { EmptyState(Icons.Default.SearchOff, "مفيش نتايج") }
            items(shown, key = { it.optString("id") }) { s ->
                val id = s.optString("id")
                val isOpen = expanded == id || q.isNotBlank()
                AppCard(onClick = { expanded = if (expanded == id) null else id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RawText(s.optString("icon"), fontSize = 26.sp)
                        Spacer(Modifier.width(10.dp))
                        RawText(KidData.t(s.optJSONObject("title")), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Icon(if (isOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                    if (isOpen) {
                        tips(s).forEach { t -> RawText("• $t", modifier = Modifier.padding(top = 6.dp), lineHeight = 22.sp) }
                        s.optString("route").takeIf { it.isNotBlank() && it != "null" }?.let { r ->
                            TextButton(onClick = { open(r) }) { Text("افتح القسم"); Icon(Icons.Default.ChevronLeft, null) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
