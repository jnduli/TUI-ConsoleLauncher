package ohi.andre.consolelauncher.commands

import android.os.Build
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Reply
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * Resolves the [CommandAbstraction.CONFIG_ENTRY] / [CommandAbstraction.CONFIG_FILE] arg types.
 *
 * [entries] and [files] are also used by
 * [ohi.andre.consolelauncher.managers.suggestions.SuggestionGenerator] to build its suggestion lists.
 */
object ConfigEntryLookup {

    /** Every config entry, across all settings files. */
    val entries: List<XMLPrefsSave> by lazy {
        val entries = ArrayList<XMLPrefsSave>()
        for (element in XMLPrefsManager.XMLPrefsRoot.entries) entries.addAll(element.enums)
        entries.addAll(Apps.values())
        entries.addAll(Notifications.values())
        entries.addAll(Reply.values())
        entries
    }

    /** Every settings file name. */
    val files: List<String> by lazy {
        val files = ArrayList<String>()
        for (element in XMLPrefsManager.XMLPrefsRoot.entries) files.add(element.path)
        files.add(AppsManager.PATH)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) files.add(ReplyManager.PATH)
        files.add(NotificationManager.PATH)
        files
    }

    fun configEntry(input: String): ArgInfo {
        val index = input.indexOf(Tuils.SPACE)

        val candidate = if (index == -1) input else input.substring(0, index)
        for (xs in entries) {
            if (xs.label() == candidate) {
                return ArgInfo(xs, if (index == -1) null else input.substring(index + 1), true, 1)
            }
        }
        return ArgInfo(null, input, false, 0)
    }

    fun configFile(input: String): ArgInfo {
        for (xs in files) {
            if (xs.equals(input, ignoreCase = true)) return ArgInfo(xs, null, true, 1)
        }
        return ArgInfo(null, input, false, 0)
    }
}
