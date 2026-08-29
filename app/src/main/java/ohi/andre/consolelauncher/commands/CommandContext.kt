package ohi.andre.consolelauncher.commands

import android.content.Context
import android.content.res.Resources
import java.io.File
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.flashlight.TorchManager
import ohi.andre.consolelauncher.managers.launchable.Launchable
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Behavior

/**
 * Carries per-invocation command state only (arg cursor, current directory, output color).
 * Managers are constructor-injected into whichever command or infrastructure piece
 * (e.g. [CommandParser], [ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager])
 * actually needs them — see how [CommandGroup] is built in MainManager.
 */
class CommandContext(
    @JvmField var context: Context,
    @JvmField var commandGroup: CommandGroup
) {
    @JvmField var args: Array<Any?>? = null
    @JvmField var currentIndex = 0

    @JvmField var currentDirectory: File = XMLPrefsManager.get(File::class.java, Behavior.home_path)!!
    @JvmField var res: Resources = context.resources
    @JvmField var cmdPrefs: CommandsPreferences = CommandsPreferences()
    @JvmField var lastCommand: String? = null
    @JvmField var commandColor: Int = TerminalManager.NO_COLOR

    @Suppress("UNCHECKED_CAST")
    fun <T> get(c: Class<T>): T? = get() as T?

    @Suppress("UNCHECKED_CAST")
    fun <T> get(c: Class<T>, index: Int): T? {
        val a = args ?: return null
        return if (index < a.size) a[index] as T? else null
    }

    fun get(): Any? {
        val a = args ?: return null
        return if (currentIndex < a.size) a[currentIndex++] else null
    }

    fun getString(): String = get() as String

    fun getInt(): Int = get() as Int

    fun getBoolean(): Boolean = get() as Boolean

    @Suppress("UNCHECKED_CAST")
    fun getList(): ArrayList<*> = get() as ArrayList<*>

    fun getPrefsSave(): XMLPrefsSave = get() as XMLPrefsSave

    fun getLaunchable(): Launchable = get() as Launchable

    fun set(args: Array<Any?>) {
        this.args = args
    }

    fun dispose() {
        val mgr = TorchManager.getInstance()
        if (mgr.isOn) mgr.turnOff()
    }

    fun clear() {
        args = null
        currentIndex = 0
        commandColor = TerminalManager.NO_COLOR
    }
}
