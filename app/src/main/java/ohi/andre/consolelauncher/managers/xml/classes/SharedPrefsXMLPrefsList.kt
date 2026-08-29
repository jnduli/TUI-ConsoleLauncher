package ohi.andre.consolelauncher.managers.xml.classes

import android.content.SharedPreferences

class SharedPrefsXMLPrefsList(private val prefs: SharedPreferences) : XMLPrefsList {

    override fun get(o: Any?): XMLPrefsEntry? {
        val key = when (o) {
            is XMLPrefsSave -> o.label()
            is String -> o
            else -> return null
        }
        val value = prefs.getString(key, null) ?: return null
        return XMLPrefsEntry(key, value)
    }

    override fun add(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun size(): Int = prefs.all.size

    override fun values(): List<String> = prefs.all.map { (k, v) -> "$k=$v" }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}
