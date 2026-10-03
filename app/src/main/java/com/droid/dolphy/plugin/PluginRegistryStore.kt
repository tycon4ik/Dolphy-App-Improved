package com.droid.dolphy.plugin

import android.content.Context
import org.json.JSONArray

/**
 * Хранилище URL-ов реестров плагинов.
 * Все URL-ы хранятся в SharedPreferences("DolphyPrefs") как JSON-массив.
 */
object PluginRegistryStore {
    private const val PREFS = "DolphyPrefs"
    private const val KEY = "plugin_registries"

    fun list(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it, null) }
        }.getOrNull().orEmpty()
    }

    fun add(context: Context, url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isBlank()) return false
        val current = list(context)
        if (current.contains(normalized)) return false
        val updated = current + normalized
        save(context, updated)
        return true
    }

    fun remove(context: Context, url: String) {
        val updated = list(context).filterNot { it == url }
        save(context, updated)
    }

    private fun save(context: Context, urls: List<String>) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, arr.toString())
            .apply()
    }
}
