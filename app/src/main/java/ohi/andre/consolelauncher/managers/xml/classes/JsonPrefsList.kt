package ohi.andre.consolelauncher.managers.xml.classes

import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@PublishedApi
internal val json = Json { ignoreUnknownKeys = true }

inline fun <reified T> loadJsonList(prefs: SharedPreferences, key: String): MutableList<T> {
    val raw = prefs.getString(key, null) ?: return mutableListOf()
    return try {
        json.decodeFromString<MutableList<T>>(raw)
    } catch (e: Exception) {
        mutableListOf()
    }
}

inline fun <reified T> saveJsonList(prefs: SharedPreferences, key: String, list: List<T>) {
    prefs.edit().putString(key, json.encodeToString(list)).apply()
}
