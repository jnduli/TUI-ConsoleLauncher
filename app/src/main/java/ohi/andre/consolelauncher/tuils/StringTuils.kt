package ohi.andre.consolelauncher.tuils

import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Ui
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

object StringTuils {

    @JvmField
    var patternNewline: Pattern = Pattern.compile("%n", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

    private val pd: Pattern = Pattern.compile("%d", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val pu: Pattern = Pattern.compile("%u", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    private val pp: Pattern = Pattern.compile("%p", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

    private fun getNicePath(filePath: String?): String {
        if (filePath == null) return "null"

        val home = XMLPrefsManager.homeDirectory().absolutePath

        return when {
            filePath == home -> "~"
            filePath.startsWith(home) -> "~" + filePath.replace(home, Tuils.EMPTYSTRING)
            else -> filePath
        }
    }

    @JvmStatic
    fun getHint(currentPath: String?): String? {
        if (!XMLPrefsManager.getBoolean(Ui.show_session_info)) return null

        var format = XMLPrefsManager.get(Behavior.session_info_format)
        if (format.isEmpty()) return null

        val deviceName = XMLPrefsManager.get(Ui.deviceName)?.ifEmpty { null } ?: android.os.Build.DEVICE
        val username = XMLPrefsManager.get(Ui.username) ?: Tuils.EMPTYSTRING

        format = pd.matcher(format).replaceAll(Matcher.quoteReplacement(deviceName))
        format = pu.matcher(format).replaceAll(Matcher.quoteReplacement(username))
        format = pp.matcher(format).replaceAll(Matcher.quoteReplacement(getNicePath(currentPath)))

        return format
    }

    @JvmStatic
    fun insertHeaders(s: MutableList<String>, newLine: Boolean) {
        val wrap = { c: Char -> if (newLine) "${Tuils.NEWLINE}$c${Tuils.NEWLINE}" else c.toString() }

        var current = 0.toChar()
        var count = 0
        while (count < s.size) {
            val c = s[count].trim().uppercase(Locale.getDefault())[0]
            if (current != c) {
                s.add(count, wrap(c))
                current = c
            }
            count++
        }
    }

    @JvmStatic
    fun addPrefix(list: MutableList<String>, prefix: String) {
        for (i in list.indices) list[i] = prefix + list[i]
    }

    @JvmStatic
    fun addSeparator(list: MutableList<String>, separator: String) {
        for (i in list.indices) list[i] = list[i] + separator
    }

    @JvmStatic
    fun printApps(apps: MutableList<String>): String {
        if (apps.isEmpty()) return apps.toString()

        // insertHeaders inserts entries in place, so this needs a real (resizable) ArrayList copy
        val list = ArrayList(apps)
        list.sortWith { a, b -> alphabeticCompare(a, b) }

        addPrefix(list, Tuils.DOUBLE_SPACE)
        insertHeaders(list, false)
        return toPlanString(list)
    }

    @JvmStatic
    @JvmOverloads
    fun toPlanString(strings: List<String>?, separator: String = Tuils.NEWLINE): String {
        return strings?.joinToString(separator) ?: Tuils.EMPTYSTRING
    }

    @JvmStatic
    fun removeUnncesarySpaces(string: String): String {
        return unnecessarySpaces.matcher(string).replaceAll(Tuils.SPACE)
    }

    private val unnecessarySpaces: Pattern = Pattern.compile("\\s{2,}")

    @JvmStatic
    fun isAlpha(s: String?): Boolean = s != null && s.all { it.isLetter() }

    @JvmStatic
    fun isPhoneNumber(s: String?): Boolean = s != null && s.none { it.isLetter() }

    @JvmStatic
    fun firstNonDigit(s: String?): Char = s?.firstOrNull { !it.isDigit() } ?: 0.toChar()

    @JvmStatic
    fun arrayContains(array: IntArray?, value: Int): Boolean = array != null && value in array

    @JvmStatic
    fun find(o: Any?, array: Array<Any?>): Int {
        return find(o, listOf(*array))
    }

    @JvmStatic
    fun find(o: Any?, list: List<*>): Int {
        for (count in list.indices) {
            val x = list[count] ?: continue

            if (o === x) return count

            if (o is XMLPrefsSave) {
                try {
                    if (o.label() == x as? String) return count
                } catch (e: Exception) {
                }
            }

            if (o is String && x is XMLPrefsSave) {
                try {
                    if (x.label() == o) return count
                } catch (e: Exception) {
                }
            }

            try {
                if (o == x || x == o) return count
            } catch (e: Exception) {
                continue
            }
        }
        return -1
    }

    @JvmStatic
    fun alphabeticCompare(s1: String, s2: String): Int {
        return removeSpaces(s1).lowercase(Locale.getDefault()).compareTo(removeSpaces(s2).lowercase(Locale.getDefault()))
    }

    private const val SPACE_REGEXP = "\\s"

    @JvmStatic
    fun removeSpaces(string: String): String {
        return string.replace(Regex(SPACE_REGEXP), Tuils.EMPTYSTRING)
    }
}
