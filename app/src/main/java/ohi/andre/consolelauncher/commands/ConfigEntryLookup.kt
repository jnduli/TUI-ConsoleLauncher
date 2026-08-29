package ohi.andre.consolelauncher.commands

import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * Resolves the [CommandAbstraction.CONFIG_ENTRY] / [CommandAbstraction.CONFIG_FILE] arg types.
 *
 * [xmlPrefsEntrys] and [xmlPrefsFiles] are also read and lazily (re)populated directly by
 * [ohi.andre.consolelauncher.managers.suggestions.SuggestionGenerator] to build its suggestion
 * lists, so they stay `@JvmField var` rather than a private-backed val.
 */
object ConfigEntryLookup {

    @JvmField var xmlPrefsEntrys: MutableList<XMLPrefsSave>? = null
    @JvmField var xmlPrefsFiles: MutableList<String>? = null

    fun configEntry(input: String): ArgInfo {
        val index = input.indexOf(Tuils.SPACE)

        var entries = xmlPrefsEntrys
        if (entries == null) {
            entries = ArrayList()

            for (element in XMLPrefsManager.XMLPrefsRoot.entries) {
                entries.addAll(element.enums)
            }
            entries.addAll(Apps.values())
            entries.addAll(Notifications.values())

            xmlPrefsEntrys = entries
        }

        val candidate = if (index == -1) input else input.substring(0, index)
        for (xs in entries) {
            if (xs.label() == candidate) {
                return ArgInfo(xs, if (index == -1) null else input.substring(index + 1), true, 1)
            }
        }
        return ArgInfo(null, input, false, 0)
    }

    fun configFile(input: String): ArgInfo {
        var files = xmlPrefsFiles
        if (files == null) {
            files = ArrayList()
            for (element in XMLPrefsManager.XMLPrefsRoot.entries) files.add(element.path)
            files.add(AppsManager.PATH)
            files.add(NotificationManager.PATH)

            xmlPrefsFiles = files
        }

        for (xs in files) {
            if (xs.equals(input, ignoreCase = true)) return ArgInfo(xs, null, true, 1)
        }
        return ArgInfo(null, input, false, 0)
    }
}
