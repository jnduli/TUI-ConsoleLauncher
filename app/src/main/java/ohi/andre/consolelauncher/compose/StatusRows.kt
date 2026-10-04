package ohi.andre.consolelauncher.compose

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Ui

/** What a home screen status row shows. [EMPTY] shows a hint until another type is picked for it. */
enum class StatusRowType(val label: String) {
    TIME("time"),
    MEMORY("memory"),
    BATTERY("battery"),
    STORAGE("storage"),
    NETWORK("network"),
    DEVICE("device name"),
    WEATHER("weather"),
    UNLOCKS("unlock counter"),
    EMPTY("empty"),
}

/** Persists the ordered list of home screen rows in the "ui" SharedPreferences. */
class StatusRowsStore(context: Context) {
    private val prefs = context.getSharedPreferences(UIManager.PREFS_NAME, 0)

    fun load(): List<StatusRowType> {
        val raw = prefs.getString(KEY, null) ?: return defaultRows()
        return try {
            Json.decodeFromString<List<StatusRowType>>(raw)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't parse status rows, falling back to defaults", e)
            defaultRows()
        }
    }

    fun save(rows: List<StatusRowType>) {
        prefs.edit { putString(KEY, Json.encodeToString(rows)) }
    }

    companion object {
        private const val TAG = "StatusRowsStore"
        private const val KEY = "status_rows"

        /** Mirrors the fixed layout the home screen had before rows became configurable. */
        fun defaultRows(): List<StatusRowType> = buildList {
            add(StatusRowType.TIME)
            add(StatusRowType.MEMORY)
            add(StatusRowType.BATTERY)
            add(StatusRowType.STORAGE)
            add(StatusRowType.NETWORK)
            if (XMLPrefsManager.getBoolean(Ui.show_device_name)) add(StatusRowType.DEVICE)
            add(StatusRowType.WEATHER)
            add(StatusRowType.UNLOCKS)
        }
    }
}
