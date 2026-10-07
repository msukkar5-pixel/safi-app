package com.mohamed.safi.data

import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import com.mohamed.safi.SafiApp
import android.content.Context

/** On-device shortcuts for navigation only; no content or private data is recorded. */
object AppShortcuts {
    private const val FAVORITES = "app_shortcut_favorites"
    private const val RECENT = "app_shortcut_recent"
    val version = mutableIntStateOf(0)
    private fun sp() = SafiApp.instance.getSharedPreferences("safi", Context.MODE_PRIVATE)

    val favorites: List<String> get() = sp().getStringSet(FAVORITES, emptySet())?.toList()?.sorted() ?: emptyList()
    val recent: List<String> get() = sp().getString(RECENT, "").orEmpty().split('|').filter { it.isNotBlank() }

    fun isFavorite(route: String) = route in favorites
    fun toggleFavorite(route: String) {
        val old = favorites.toSet()
        sp().edit { putStringSet(FAVORITES, if (route in old) old - route else old + route) }
        version.intValue++
    }

    fun record(route: String) {
        if (route.isBlank() || route in setOf("home", "welcome", "search", "kidhome")) return
        val changed = (listOf(route) + recent.filter { it != route }).take(8)
        sp().edit { putString(RECENT, changed.joinToString("|")) }
        version.intValue++
    }
}
