package ohi.andre.consolelauncher.managers

import android.content.Context
import android.text.SpannableString
import kotlinx.serialization.Serializable
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.loadJsonList
import ohi.andre.consolelauncher.managers.xml.classes.saveJsonList
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.ViewTuils
import java.util.regex.Pattern

/**
 * Created by francescoandreuzzi on 04/10/2017.
 */
class RegexManager(private val context: Context) {

    @Serializable
    data class RegexRecord(val id: Int, val value: String)

    private val regexes: MutableList<Regex> = ArrayList()

    init {
        instance = this

        object : StoppableThread() {
            override fun run() {
                super.run()

                try {
                    val prefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
                    val records = loadJsonList<RegexRecord>(prefs, REGEXES_KEY)
                    for (record in records) {
                        regexes.add(Regex(record.value, record.id))
                    }
                } catch (e: Exception) {
                }
            }
        }.start()
    }

    fun get(id: Int): Regex? = regexes.find { it.id == id }

    // null: all good
    // "": used id
    fun add(id: Int, value: String): String? {
        if (regexes.any { it.id == id }) return Tuils.EMPTYSTRING

        regexes.add(Regex(value, id))

        val prefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
        val records = loadJsonList<RegexRecord>(prefs, REGEXES_KEY)
        records.add(RegexRecord(id, value))
        saveJsonList(prefs, REGEXES_KEY, records)

        return null
    }

    // null: all good
    // "": not found
    fun rm(id: Int): String? {
        val prefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
        val records = loadJsonList<RegexRecord>(prefs, REGEXES_KEY)
        val removed = records.removeIf { it.id == id }
        if (!removed) return Tuils.EMPTYSTRING

        saveJsonList(prefs, REGEXES_KEY, records)
        regexes.removeIf { it.id == id }
        return null
    }

    fun test(id: Int, test: String): CharSequence {
        val regex = get(id) ?: return Tuils.EMPTYSTRING

        if (regex.regex == null) return "null"
        val m = regex.regex!!.matcher(test)

        val color = XMLPrefsManager.getColor(Theme.mark_color)
        val outputColor = XMLPrefsManager.getColor(Theme.output_color)

        if (m.matches()) {
            return ViewTuils.span(test, outputColor, color)
        }

        var last = 0
        val s = ViewTuils.span(test, outputColor)
        while (m.find()) {
            val g0 = m.group(0)
            last = ViewTuils.highlightSubstring(color, s, g0, last)
        }

        return s
    }

    fun dispose() {
        regexes.clear()
        instance = null
    }

    class Regex {
        @JvmField
        var regex: Pattern? = null
        @JvmField
        var literalPattern: String? = null
        @JvmField
        var id: Int = 0

        constructor()

        constructor(value: String, id: Int) {
            this.regex = Pattern.compile(value)
            this.id = id
        }
    }

    companion object {
        private const val PATH = "regex.xml"
        private const val REGEXES_KEY = "regexes"

        @JvmField
        var instance: RegexManager? = null
    }
}
