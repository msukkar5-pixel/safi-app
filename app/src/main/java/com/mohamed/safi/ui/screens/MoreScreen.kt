package com.mohamed.safi.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohamed.safi.ui.*

private data class Entry(val route: String, val title: String, val desc: String, val icon: ImageVector, val color: Color)

private val entries = listOf(
    Entry("transfers", "تحويلات مصر", "ماما، البيت، الدروس…", Icons.Default.SwapHoriz, Warn),
    Entry("bills", "الفواتير والالتزامات", "إيجار، كهرباء، اتصالات", Icons.Default.Payments, Brand),
    Entry("debts", "السلف والديون", "ليك وعليك ومواعيدها", Icons.Default.People, Danger),
    Entry("car", "العربية", "بنزين، صيانة، أوراق", Icons.Default.DirectionsCar, Color2),
    Entry("places", "أماكني", "كل مكان رحته بالوقت", Icons.Default.Place, Color(0xFF8E5BB8)),
    Entry("reports", "التقارير والميزانية", "صرفت إيه وفين", Icons.Default.BarChart, Positive),
    Entry("settings", "الإعدادات", "المفتاح، الصلاحيات، السعر", Icons.Default.Settings, Color(0xFF6C7A89)),
)

@Composable
fun MoreScreen(open: (String) -> Unit) {
    ScreenScaffold("المزيد") { pad ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries) { e ->
                AppCard(onClick = { open(e.route) }) {
                    CatBadge(e.title, 44, e.icon, e.color)
                    Spacer(Modifier.height(10.dp))
                    Text(e.title, fontWeight = FontWeight.Bold)
                    Text(e.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
