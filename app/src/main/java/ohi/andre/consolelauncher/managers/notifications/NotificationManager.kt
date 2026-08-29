package ohi.andre.consolelauncher.managers.notifications

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import kotlinx.serialization.Serializable
import ohi.andre.consolelauncher.managers.RegexManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.SharedPrefsXMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.classes.loadJsonList
import ohi.andre.consolelauncher.managers.xml.classes.saveJsonList
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.tuils.Tuils
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by francescoandreuzzi on 29/04/2017.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
class NotificationManager private constructor(context: Context) : XMLPrefsElement {

    @Serializable
    data class NotificatedApp(
        val pkg: String,
        val color: String? = null,
        val format: String? = null,
        val enabled: Boolean = false,
    )

    @Serializable
    data class FilterRecord(val id: Int, val value: String)

    @Serializable
    data class FormatRecord(val id: Int, val value: String)

    private var values: XMLPrefsList = SharedPrefsXMLPrefsList(
        context.getSharedPreferences(PATH, Context.MODE_PRIVATE),
    )
    private var apps: MutableList<NotificatedApp> = mutableListOf()
    private var filters: MutableList<Pattern> = mutableListOf()
    private var formats: MutableList<FormatRecord> = mutableListOf()

    var default_app_state: Boolean = false
        private set
    var default_color: String? = null
        private set

    override fun delete(): Array<String?>? = null

    override fun getValues(): XMLPrefsList = values

    override fun write(c: Context, save: XMLPrefsSave, value: String) {
        values.add(save.label(), value)
    }

    override fun path(): String = PATH

    init {
        instance = this

        val prefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
        XMLPrefsManager.seedSharedPrefsDefaults(prefs, Notifications.values().toList())

        apps = loadJsonList<NotificatedApp>(prefs, APPS_KEY)
        formats = loadJsonList<FormatRecord>(prefs, FORMATS_KEY)
        reloadFilters(loadJsonList(prefs, FILTERS_KEY))

        for (i in apps.indices) {
            val app = apps[i]
            val formatId = app.format?.toIntOrNull() ?: continue
            val format = formats.find { it.id == formatId } ?: continue
            apps[i] = app.copy(format = format.value)
        }

        default_app_state = XMLPrefsManager.getBoolean(Notifications.app_notification_enabled_default)
        default_color = XMLPrefsManager.get(Notifications.default_notification_color)
    }

    private fun reloadFilters(records: List<FilterRecord>) {
        filters = records.map { record ->
            val regex = record.value
            try {
                val id = regex.toInt()
                RegexManager.instance!!.get(id)!!.regex!!
            } catch (e: Exception) {
                try {
                    Pattern.compile(regex)
                } catch (e2: Exception) {
                    Pattern.compile(regex, Pattern.LITERAL)
                }
            }
        }.toMutableList()
    }

    fun dispose() {
        values.clear()
        apps.clear()
        filters.clear()
        formats.clear()

        instance = null
    }

    fun match(text: String): Boolean {
        for (f in filters) {
            val m: Matcher = f.matcher(text)
            if (m.matches() || m.find() || text == f.pattern()) return true
        }
        return false
    }

    fun apps(): Int = apps.size

    fun getAppState(pkg: String): NotificatedApp? = apps.find { it.pkg == pkg }

    companion object {
        const val ENABLED_ATTRIBUTE = "enabled"
        const val ID_ATTRIBUTE = "id"
        const val FORMAT_ATTRIBUTE = "format"
        const val FILTER_ATTRIBUTE = "filter"

        const val PATH = "notifications.xml"

        private const val APPS_KEY = "apps"
        private const val FILTERS_KEY = "filters"
        private const val FORMATS_KEY = "formats"

        @JvmField
        var instance: NotificationManager? = null

        @JvmStatic
        fun create(context: Context): NotificationManager = instance ?: NotificationManager(context)

        @JvmStatic
        fun setState(c: Context, pkg: String, state: Boolean): String? {
            upsertApp(c, pkg) { it.copy(enabled = state) }
            return null
        }

        @JvmStatic
        fun setColor(c: Context, pkg: String, color: String): String? {
            upsertApp(c, pkg) { it.copy(enabled = true, color = color) }
            return null
        }

        @JvmStatic
        fun setFormat(c: Context, pkg: String, format: String): String? {
            upsertApp(c, pkg) { it.copy(format = format) }
            return null
        }

        private fun upsertApp(c: Context, pkg: String, mutate: (NotificatedApp) -> NotificatedApp) {
            val prefs = c.getSharedPreferences(PATH, Context.MODE_PRIVATE)
            val apps = loadJsonList<NotificatedApp>(prefs, APPS_KEY)
            val index = apps.indexOfFirst { it.pkg == pkg }
            if (index >= 0) apps[index] = mutate(apps[index])
            else apps.add(mutate(NotificatedApp(pkg)))
            saveJsonList(prefs, APPS_KEY, apps)
            instance?.apps = apps
        }

        @JvmStatic
        fun addFilter(c: Context, pattern: String, id: Int): String? {
            val prefs = c.getSharedPreferences(PATH, Context.MODE_PRIVATE)
            val filters = loadJsonList<FilterRecord>(prefs, FILTERS_KEY)
            filters.add(FilterRecord(id, pattern))
            saveJsonList(prefs, FILTERS_KEY, filters)
            instance?.reloadFilters(filters)
            return null
        }

        @JvmStatic
        fun addFormat(c: Context, format: String, id: Int): String? {
            val prefs = c.getSharedPreferences(PATH, Context.MODE_PRIVATE)
            val formats = loadJsonList<FormatRecord>(prefs, FORMATS_KEY)
            formats.add(FormatRecord(id, format))
            saveJsonList(prefs, FORMATS_KEY, formats)
            instance?.formats = formats
            return null
        }

        @JvmStatic
        fun rmFilter(c: Context, id: Int): String? {
            val prefs = c.getSharedPreferences(PATH, Context.MODE_PRIVATE)
            val filters = loadJsonList<FilterRecord>(prefs, FILTERS_KEY)
            val removed = filters.removeIf { it.id == id }
            saveJsonList(prefs, FILTERS_KEY, filters)
            instance?.reloadFilters(filters)
            return if (removed) null else Tuils.EMPTYSTRING
        }

        @JvmStatic
        fun rmFormat(c: Context, id: Int): String? {
            val prefs = c.getSharedPreferences(PATH, Context.MODE_PRIVATE)
            val formats = loadJsonList<FormatRecord>(prefs, FORMATS_KEY)
            val removed = formats.removeIf { it.id == id }
            saveJsonList(prefs, FORMATS_KEY, formats)
            instance?.formats = formats
            return if (removed) null else Tuils.EMPTYSTRING
        }
    }
}
