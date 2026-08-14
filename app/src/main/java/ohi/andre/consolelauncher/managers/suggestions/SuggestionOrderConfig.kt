package ohi.andre.consolelauncher.managers.suggestions

import android.content.Context
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.tuils.FileTuils
import org.w3c.dom.Element
import java.io.File

/** Which suggestion list an ordering [SuggestionOrderEntry] applies to. */
enum class SuggestionOrderMode(val attrValue: String) {
    /** The input field has text in it. */
    INPUT("input"),

    /** The input field is empty. */
    NO_INPUT("noinput"),
}

/** One row of the user-editable suggestions-order config: how a [SuggestionCategory] is
 * prioritized ([order], lower sorts first) and capped ([count]) in a given [mode]. */
data class SuggestionOrderEntry(
    val mode: SuggestionOrderMode,
    val category: SuggestionCategory,
    val order: Int,
    val count: Int,
)

/**
 * Reads (and, on first run, seeds) `suggestions_order.xml` - a flat list of
 * `<entry mode="..." type="..." order="..." count="..."/>` records, one per (mode, category) pair.
 * Replaces the old packed `suggestions_order`/`noinput_suggestions_order` string settings, whose
 * `"cmd(2)app(5)alias(5)appgroup(3)"` mini-language required a custom parser to make sense of.
 *
 * Modeled on [ohi.andre.consolelauncher.managers.HTMLExtractManager]'s `StoreableValue` - the only
 * other precedent in this codebase for a structured, multi-record XML list, as opposed to the
 * single flat `<key value="..."/>` settings most of [ohi.andre.consolelauncher.managers.xml.options]
 * uses.
 */
object SuggestionOrderConfig {
    private const val PATH = "suggestions_order.xml"
    private const val ROOT = "SUGGESTIONS_ORDER"
    private const val ENTRY_TAG = "entry"

    private const val MODE_ATTR = "mode"
    private const val TYPE_ATTR = "type"
    private const val ORDER_ATTR = "order"
    private const val COUNT_ATTR = "count"

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

    /** Reads all entries, seeding the file with [DEFAULT_ENTRIES] if it's missing or empty
     * (fresh install, or an upgrade from the old string-based settings). */
    fun load(context: Context?): List<SuggestionOrderEntry> {
        val file = File(FileTuils.getFolder(context), PATH)
        if (!file.exists()) {
            seedDefaults(file)
        }

        val entries = readEntries(file)
        if (entries.isNotEmpty()) return entries

        seedDefaults(file)
        return readEntries(file)
    }

    private fun readEntries(file: File): List<SuggestionOrderEntry> {
        return try {
            val document = XMLPrefsManager.buildDocument(file, ROOT) ?: return emptyList()
            val root = document[1] as Element
            val nodes = root.getElementsByTagName(ENTRY_TAG)
            (0 until nodes.length).mapNotNull { i -> entryFromNode(nodes.item(i) as Element) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun entryFromNode(e: Element): SuggestionOrderEntry? {
        val modeAttr = XMLPrefsManager.getStringAttribute(e, MODE_ATTR) ?: return null
        val typeAttr = XMLPrefsManager.getStringAttribute(e, TYPE_ATTR) ?: return null

        val mode = SuggestionOrderMode.entries.firstOrNull { it.attrValue == modeAttr } ?: return null
        val category = SuggestionCategory.entries.firstOrNull { it.configKey == typeAttr } ?: return null

        val order = XMLPrefsManager.getIntAttribute(e, ORDER_ATTR)
        val count = XMLPrefsManager.getIntAttribute(e, COUNT_ATTR)
        if (order < 0 || count < 0) return null

        return SuggestionOrderEntry(mode, category, order, count)
    }

    private fun seedDefaults(file: File) {
        XMLPrefsManager.resetFile(file, ROOT)
        for (entry in DEFAULT_ENTRIES) {
            XMLPrefsManager.add(
                file,
                ENTRY_TAG,
                arrayOf(MODE_ATTR, TYPE_ATTR, ORDER_ATTR, COUNT_ATTR),
                arrayOf(entry.mode.attrValue, entry.category.configKey, entry.order.toString(), entry.count.toString()),
            )
        }
    }
}
