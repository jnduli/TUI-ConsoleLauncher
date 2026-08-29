package ohi.andre.consolelauncher.commands.main.raw

import android.content.ActivityNotFoundException
import java.io.File
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.UIManager.Companion.PREFS_NAME
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.CONFIG_ENTRY
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.CONFIG_FILE
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.FILE
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.INT
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.PLAIN_TEXT
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.FileTuils
import ohi.andre.consolelauncher.tuils.LogTuils
import ohi.andre.consolelauncher.tuils.StringTuils
import ohi.andre.consolelauncher.tuils.SystemUtils
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.ViewTuils
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable

class config : ParamCommand() {

    private abstract class Param(private val paramName: String) : ohi.andre.consolelauncher.commands.main.Param {
        override fun label(): String = Tuils.MINUS + paramName

        override fun onNotArgEnough(pack: CommandContext, n: Int): String? {
            return pack.context.getString(R.string.help_config)
        }

        override fun onArgNotFound(pack: CommandContext, index: Int): String {
            return pack.context.getString(R.string.output_invalidarg)
        }
    }

    private val set: Param = object : Param("set") {
        override fun args(): IntArray = intArrayOf(CONFIG_ENTRY, PLAIN_TEXT)

        override fun exec(pack: CommandContext): String? {
            val save = pack.getPrefsSave()
            val value = pack.getString()
            save.parent().write(pack.context, save, value)

            (pack.context as Reloadable).addMessage(save.parent().path(), save.label() + " -> " + value)

            if (save.label().startsWith("default_app_n")) {
                return pack.context.getString(R.string.output_usedefapp)
            } else if (save == Behavior.unlock_counter_cycle_start) {
                val preferences = pack.context.getSharedPreferences(PREFS_NAME, 0)
                preferences.edit()
                    .putLong(UIManager.NEXT_UNLOCK_CYCLE_RESTART, 0)
                    .putInt(UIManager.UNLOCK_KEY, 0)
                    .apply()
            }

            return null
        }

        override fun onNotArgEnough(pack: CommandContext, n: Int): String? {
            pack.args = arrayOf(pack.args!![1], Tuils.EMPTYSTRING)
            return set.exec(pack)
        }
    }

    private val info = object : Param("info") {
        override fun args(): IntArray = intArrayOf(CONFIG_ENTRY)

        override fun exec(pack: CommandContext): String {
            val save = pack.getPrefsSave()

            return "Type:" + Tuils.SPACE + save.type() + Tuils.NEWLINE +
                "Default:" + Tuils.SPACE + save.defaultValue() + Tuils.NEWLINE +
                save.info()
        }
    }

    private val file = object : Param("file") {
        override fun args(): IntArray = intArrayOf(CONFIG_FILE)

        override fun exec(pack: CommandContext): String? {
            val file = File(FileTuils.getFolder(pack.context), pack.getString())

            try {
                pack.context.startActivity(FileTuils.openFile(pack.context, file))
            } catch (e: ActivityNotFoundException) {
                LogTuils.log("nf")
                LogTuils.toFile(e)
            } catch (ex: Exception) {
                LogTuils.log(ex)
                LogTuils.toFile(ex)
            }

            return null
        }

        override fun onArgNotFound(pack: CommandContext, index: Int): String {
            return pack.context.getString(R.string.output_filenotfound)
        }
    }

    private val append = object : Param("append") {
        override fun args(): IntArray = intArrayOf(CONFIG_ENTRY, PLAIN_TEXT)

        override fun exec(pack: CommandContext): String? {
            val save = pack.getPrefsSave()
            val value = XMLPrefsManager.get(save) + pack.getString()

            save.parent().write(pack.context, save, value)

            (pack.context as Reloadable).addMessage(save.parent().path(), save.label() + " -> " + value)

            return null
        }

        override fun onNotArgEnough(pack: CommandContext, n: Int): String? {
            pack.args = arrayOf(pack.args!![0], pack.args!![1], Tuils.EMPTYSTRING)
            return set.exec(pack)
        }
    }

    private val erase = object : Param("erase") {
        override fun args(): IntArray = intArrayOf(CONFIG_ENTRY)

        override fun exec(pack: CommandContext): String? {
            val save = pack.getPrefsSave()
            save.parent().write(pack.context, save, Tuils.EMPTYSTRING)

            (pack.context as Reloadable).addMessage(save.parent().path(), save.label() + " -> " + "\"\"")

            return null
        }
    }

    private val get = object : Param("get") {
        override fun args(): IntArray = intArrayOf(CONFIG_ENTRY)

        override fun exec(pack: CommandContext): String {
            val save = pack.getPrefsSave()
            val s = XMLPrefsManager.get(String::class.java, save)
            if (s.isNullOrEmpty()) return "\"\""
            return s
        }
    }

    private val ls = object : Param("ls") {
        override fun args(): IntArray = intArrayOf(CONFIG_FILE)

        override fun exec(pack: CommandContext): String {
            val file = File(FileTuils.getFolder(pack.context), pack.getString())
            val name = file.name

            for (r in XMLPrefsManager.XMLPrefsRoot.entries) {
                if (name.equals(r.path, ignoreCase = true)) {
                    val strings = ArrayList(r.getValues().values())
                    StringTuils.addPrefix(strings, Tuils.DOUBLE_SPACE)
                    strings.add(0, r.path)
                    return StringTuils.toPlanString(strings, Tuils.NEWLINE)
                }
            }

            if (name.equals(AppsManager.PATH, ignoreCase = true)) {
                val strings = ArrayList(AppsManager.instance!!.getValues().values())
                StringTuils.addPrefix(strings, Tuils.DOUBLE_SPACE)
                strings.add(0, AppsManager.PATH)
                return StringTuils.toPlanString(strings, Tuils.NEWLINE)
            }

            if (name.equals(NotificationManager.PATH, ignoreCase = true)) {
                val strings = ArrayList(NotificationManager.instance!!.getValues().values())
                StringTuils.addPrefix(strings, Tuils.DOUBLE_SPACE)
                strings.add(0, NotificationManager.PATH)
                return StringTuils.toPlanString(strings, Tuils.NEWLINE)
            }

            return "[]"
        }

        override fun onArgNotFound(pack: CommandContext, index: Int): String {
            return pack.context.getString(R.string.output_filenotfound)
        }

        override fun onNotArgEnough(pack: CommandContext, n: Int): String {
            val ss = ArrayList<String>()

            for (element in XMLPrefsManager.XMLPrefsRoot.entries) {
                ss.add(element.path)
                for (save in element.enums) {
                    ss.add(Tuils.DOUBLE_SPACE + save.label())
                }
            }
            ss.add(AppsManager.PATH)
            for (save in Apps.values()) {
                ss.add(Tuils.DOUBLE_SPACE + save.label())
            }
            ss.add(NotificationManager.PATH)
            for (save in Notifications.values()) {
                ss.add(Tuils.DOUBLE_SPACE + save.label())
            }

            return StringTuils.toPlanString(ss)
        }
    }

    private val fontsize = object : Param("fontsize") {
        override fun args(): IntArray = intArrayOf(INT)

        override fun exec(pack: CommandContext): String? {
            val parent = Ui.device_size.parent()

            val size = pack.getInt()

            parent.write(pack.context, Ui.device_size, size.toString())
            parent.write(pack.context, Ui.ram_size, size.toString())
            parent.write(pack.context, Ui.network_size, size.toString())
            parent.write(pack.context, Ui.storage_size, size.toString())
            parent.write(pack.context, Ui.battery_size, size.toString())
            parent.write(pack.context, Ui.time_size, size.toString())
            parent.write(pack.context, Ui.weather_size, size.toString())
            parent.write(pack.context, Ui.unlock_size, size.toString())
            parent.write(pack.context, Ui.input_output_size, size.toString())

            return null
        }
    }

    private val reset = object : Param("reset") {
        override fun args(): IntArray = intArrayOf(CONFIG_ENTRY)

        override fun exec(pack: CommandContext): String? {
            val save = pack.getPrefsSave()
            save.parent().write(pack.context, save, save.defaultValue())

            (pack.context as Reloadable).addMessage(save.parent().path(), save.label() + " -> " + save.defaultValue())

            return null
        }
    }

    private val factory = object : Param("factory") {
        override fun args(): IntArray = IntArray(0)

        override fun exec(pack: CommandContext): String? {
            for (root in XMLPrefsManager.XMLPrefsRoot.entries) {
                root.getValues().clear()
            }

            for (save in Notifications.values()) {
                save.parent().write(pack.context, save, save.defaultValue())
            }

            (pack.context as Reloadable).addMessage("config", "factory reset")
            (pack.context as Reloadable).reload()

            return null
        }
    }

    private val apply = object : Param("apply") {
        override fun args(): IntArray = intArrayOf(FILE)

        override fun exec(pack: CommandContext): String {
            val file = pack.get(File::class.java)!!

            if (!file.name.endsWith(".xml")) {
                // is font
                if (ViewTuils.fontPath != null) {
                    val font = File(ViewTuils.fontPath!!)
                    if (font.exists()) {
                        val files = font.listFiles()
                        if (files != null && files.isNotEmpty()) FileTuils.insertOld(pack.context, files[0])
                        FileTuils.deleteContentOnly(font)
                    } else {
                        font.mkdir()
                    }
                }
            } else {
                val toPutInsideOld = File(FileTuils.getFolder(pack.context), file.name)
                FileTuils.insertOld(pack.context, toPutInsideOld)
            }

            val dest = File(FileTuils.getFolder(pack.context), file.name)
            file.renameTo(dest)

            return "Path: " + dest.absolutePath
        }
    }

    private val tutorial = object : Param("tutorial") {
        override fun args(): IntArray = IntArray(0)

        override fun exec(pack: CommandContext): String? {
            pack.context.startActivity(SystemUtils.webPage("https://github.com/Andre1299/TUI-ConsoleLauncher/wiki/Customize-T_UI"))
            return null
        }
    }

    private fun values(): Array<ohi.andre.consolelauncher.commands.main.Param> = arrayOf(
        set, info, file, append, erase, get, ls, fontsize, reset, factory, apply, tutorial
    )

    override fun paramForString(
        pack: CommandContext,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        return ohi.andre.consolelauncher.commands.main.Param.find(values(), param)
    }

    override fun doThings(pack: CommandContext): String? {
        return null
    }

    override fun priority(): Int {
        return 4
    }

    override fun helpRes(): Int {
        return R.string.help_config
    }

    override fun params(): Array<String> {
        return ohi.andre.consolelauncher.commands.main.Param.labels(values())
    }
}
