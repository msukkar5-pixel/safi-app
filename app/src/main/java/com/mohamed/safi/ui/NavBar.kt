package com.mohamed.safi.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.mohamed.safi.SafiApp

data class NavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Bottom bar: 6 places. Home, Safi (assistant) and More are fixed; the other 3 are chosen by the user.
 * Layout: Home, slot 1, slot 2, Safi, slot 3, More.
 */
object NavPrefs {
    val home = NavItem("home", "الرئيسية", Icons.Default.Home)
    val safi get() = NavItem("assistant", com.mohamed.safi.AppName.v, Icons.Default.Mic)
    val more = NavItem("more", "المزيد", Icons.Default.GridView)

    /** Everything that can sit in a free slot. */
    val choices = listOf(
        NavItem("finance", "الحسابات", Icons.Default.AccountBalanceWallet),
        NavItem("schedule", "المواعيد", Icons.Default.Event),
        NavItem("prayer", "الصلاة", Icons.Default.Mosque),
        NavItem("quran", "القرآن", Icons.Default.MenuBook),
        NavItem("azkar", "الأذكار", Icons.Default.Favorite),
        NavItem("hisn", "حصن المسلم", Icons.Default.Shield),
        NavItem("wird", "الورد", Icons.Default.AutoStories),
        NavItem("quranaudio", "القرآن المسموع", Icons.Default.Headphones),
        NavItem("radio", "الإذاعات", Icons.Default.Radio),
        NavItem("library", "المكتبة", Icons.Default.LocalLibrary),
        NavItem("kids", "الأطفال", Icons.Default.ChildCare),
        NavItem("sleep", "قبل النوم", Icons.Default.Bedtime),
        NavItem("fitness", "الصحة", Icons.Default.FitnessCenter),
        NavItem("vehicle", "السيارة", Icons.Default.DirectionsCar),
        NavItem("documents", "المستندات", Icons.Default.Badge),
        NavItem("diary", "مذكراتي", Icons.Default.EditNote),
        NavItem("quiz", "المسابقة", Icons.Default.EmojiEvents),
        NavItem("tv", "القنوات", Icons.Default.LiveTv),
    )
    private val defaults = listOf("finance", "schedule", "prayer")

    private fun sp() = SafiApp.instance.getSharedPreferences("safi_nav", Context.MODE_PRIVATE)
    private fun load() = (sp().getString("slots", null)?.split(",")?.filter { r -> choices.any { it.route == r } } ?: defaults)
        .let { (it + defaults.filter { d -> d !in it }).take(3) }

    val slots = mutableStateOf(load())
    fun setSlots(v: List<String>) { slots.value = v.take(3); sp().edit { putString("slots", slots.value.joinToString(",")) } }

    fun items(): List<NavItem> {
        val s = slots.value.mapNotNull { r -> choices.firstOrNull { it.route == r } }
        return listOf(home, s[0], s[1], safi, s[2], more)
    }
    fun routes() = items().map { it.route }.toSet()
}

/** Pick the 3 free places of the bottom bar. */
@Composable
fun NavCustomizeDialog(onDismiss: () -> Unit) {
    val chosen = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateListOf(*NavPrefs.slots.value.toTypedArray()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("القايمة اللي تحت") },
        text = {
            Column {
                Text("الرئيسية و${com.mohamed.safi.AppName.v} والمزيد ثابتين. اختار ٣ حاجات تانية بالترتيب.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.heightIn(max = 380.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(NavPrefs.choices) { c ->
                        val idx = chosen.indexOf(c.route)
                        val sel = idx >= 0
                        OutlinedCard(
                            onClick = {
                                if (sel) chosen.remove(c.route)
                                else if (chosen.size < 3) chosen.add(c.route)
                                else { chosen.removeAt(0); chosen.add(c.route) }
                            },
                            colors = CardDefaults.outlinedCardColors(containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                        ) {
                            Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(c.icon, null, tint = MaterialTheme.colorScheme.primary)
                                Text(if (sel) "${idx + 1}. ${tr(c.label)}" else c.label, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { NavPrefs.setSlots(chosen.toList()); onDismiss() }, enabled = chosen.size == 3) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
