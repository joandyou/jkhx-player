package com.jkhx.player.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SearchHistoryManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jkhx_search_history_pref", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true }

    private val _history = MutableStateFlow<List<String>>(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private fun loadHistory(): List<String> {
        val raw = prefs.getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<String>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return

        val current = _history.value.toMutableList()
        current.removeAll { it.equals(trimmed, ignoreCase = true) }
        current.add(0, trimmed)
        val updated = current.take(20)

        prefs.edit().putString(KEY_SEARCH_HISTORY, json.encodeToString(updated)).apply()
        _history.value = updated
    }

    fun removeQuery(query: String) {
        val current = _history.value.toMutableList()
        current.removeAll { it.equals(query, ignoreCase = true) }

        prefs.edit().putString(KEY_SEARCH_HISTORY, json.encodeToString(current)).apply()
        _history.value = current
    }

    fun clearAll() {
        prefs.edit().remove(KEY_SEARCH_HISTORY).apply()
        _history.value = emptyList()
    }

    companion object {
        private const val KEY_SEARCH_HISTORY = "search_history_items"
    }
}
