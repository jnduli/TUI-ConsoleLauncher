package ohi.andre.consolelauncher.managers.suggestions

import android.content.Context
import kotlinx.serialization.Serializable
import ohi.andre.consolelauncher.managers.xml.classes.loadJsonList
import ohi.andre.consolelauncher.managers.xml.classes.saveJsonList

/** Which suggestion list an ordering [SuggestionOrderEntry] applies to. */
enum class SuggestionOrderMode(val attrValue: String) {
    /** The input field has text in it. */
    INPUT("input"),

    /** The input field is empty. */
    NO_INPUT("noinput"),
}

/** One row of the user-editable suggestions-order config: how a [SuggestionCategory] is
 * prioritized ([order], lower sorts first) and capped ([count]) in a given [mode]. */
@Serializable
data class SuggestionOrderEntry(
    val mode: SuggestionOrderMode,
    val category: SuggestionCategory,
    val order: Int,
    val count: Int,
)

/**
 * Reads (and, on first run, seeds) the suggestions-order config - a fixed set of entries, one per
 * (mode, category) pair, stored as a single JSON list in `SharedPreferences`. Replaces the old
 * packed `suggestions_order`/`noinput_suggestions_order` string settings, whose
 * `"cmd(2)app(5)alias(5)appgroup(3)"` mini-language required a custom parser to make sense of.
 */
object SuggestionOrderConfig {
    private const val PATH = "suggestions_order.xml"
    private const val ENTRIES_KEY = "entries"

    private val DEFAULT_ENTRIES = listOf(
        SuggestionOrderEntry(SuggestionOrderMode.INPUT, SuggestionCategory.COMMAND, 1, 2),
        SuggestionOrderEntry(SuggestionOrderMode.INPUT, SuggestionCategory.APP, 2, 5),
        SuggestionOrderEntry(SuggestionOrderMode.INPUT, SuggestionCategory.ALIAS, 3, 5),
        SuggestionOrderEntry(SuggestionOrderMode.INPUT, SuggestionCategory.APP_GROUP, 4, 3),

        SuggestionOrderEntry(SuggestionOrderMode.NO_INPUT, SuggestionCategory.APP, 1, 5),
        SuggestionOrderEntry(SuggestionOrderMode.NO_INPUT, SuggestionCategory.ALIAS, 2, 5),
        SuggestionOrderEntry(SuggestionOrderMode.NO_INPUT, SuggestionCategory.COMMAND, 3, 2),
        SuggestionOrderEntry(SuggestionOrderMode.NO_INPUT, SuggestionCategory.APP_GROUP, 4, 5),
    )

    /** Reads all entries, seeding [DEFAULT_ENTRIES] if none are stored yet
     * (fresh install, or an upgrade from the old string-based settings). */
    fun load(context: Context?): List<SuggestionOrderEntry> {
        val prefs = context!!.getSharedPreferences(PATH, Context.MODE_PRIVATE)

        val entries = loadJsonList<SuggestionOrderEntry>(prefs, ENTRIES_KEY)
        if (entries.isNotEmpty()) return entries

        saveJsonList(prefs, ENTRIES_KEY, DEFAULT_ENTRIES)
        return DEFAULT_ENTRIES
    }
}
