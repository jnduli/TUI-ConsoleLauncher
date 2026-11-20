package ohi.andre.consolelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Parcelable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.commands.CommandParser
import ohi.andre.consolelauncher.commands.main.raw.*
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.tuils.StringTuils.printApps
import ohi.andre.consolelauncher.managers.launchable.WebLauncher
import ohi.andre.consolelauncher.managers.ChangelogManager
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.launchable.Launchable
import ohi.andre.consolelauncher.managers.MessagesManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.ThemeManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.TuiLocationManager
import ohi.andre.consolelauncher.managers.notifications.KeeperService
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.LogTuils
import ohi.andre.consolelauncher.tuils.OutputTuils
import ohi.andre.consolelauncher.tuils.StringTuils

import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell.Interactive
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell.OnCommandResultListener
import ohi.andre.consolelauncher.tuils.libsuperuser.ShellHolder
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern

/*Copyright Francesco Andreuzzi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/
class MainManager constructor(private var mContext: LauncherActivity) {
    private val triggers = arrayOf<CmdTrigger>(
        GroupTrigger(),
        AliasTrigger(),
        TuiCommandTrigger(),
        AppTrigger(),
        ShellCommandTrigger()
    )
    val mainPack: CommandContext

    private val showAliasValue: Boolean
    private val aliasContentColor: Int

    val aliasManager: AliasManager
    val appsManager: AppsManager
    var contactManager: ContactManager? = null
        private set
    private val themeManager: ThemeManager
    private val htmlExtractManager: HTMLExtractManager

    var messagesManager: MessagesManager? = null

    private val receiver: BroadcastReceiver

    private val keeperServiceRunning: Boolean

    private fun updateServices(cmd: String?) {
        if (keeperServiceRunning) {
            val i = Intent(mContext, KeeperService::class.java)
            i.putExtra(KeeperService.CMD_KEY, cmd)
            i.putExtra(KeeperService.PATH_KEY, mainPack.currentDirectory.getAbsolutePath())
            mContext.startService(i)
        }
    }

    fun onCommand(input: String, launchable: Launchable?) {
        if (launchable == null) {
            onCommand(input, null as String?)
            return
        }

        updateServices(input)

        val unspacedLabel = StringTuils.removeSpaces(launchable.name().lowercase(Locale.getDefault()))
        if (unspacedLabel == StringTuils.removeSpaces(input.lowercase(Locale.getDefault()))) {
            appsManager.performLaunch(launchable)
        } else {
            onCommand(input, null as String?)
        }
    }

    var colorExtractor: Pattern =
        Pattern.compile("(#[^(]{6})\\[([^\\)]*)\\]", Pattern.CASE_INSENSITIVE)

    fun onCommand(input: String, alias: String?) {
        val input = StringTuils.removeUnncesarySpaces(input)

        if (alias == null) updateServices(input)

        if (alias != null && showAliasValue) {
            OutputTuils.sendOutput(mContext, aliasManager.formatLabel(alias, input), aliasContentColor)
        }

        val (cmd, color) = colorize(input ?: return)
        executeCmd(cmd, color)
    }

    private fun colorize(input: String): Pair<String?, Int> {
        val m = colorExtractor.matcher(input)
        if (m.matches()) {
            try {
                return Pair(m.group(2), Color.parseColor(m.group(1)))
            } catch (e: Exception) {
                return Pair(input, TerminalManager.NO_COLOR)
            }
        }
        return Pair(input, TerminalManager.NO_COLOR)
    }

    private fun executeCmd(cmd: String?, color: Int) {
        if (cmd == null) return
        mainPack.clear()
        mainPack.commandColor = color
        for (trigger in triggers) {
            val triggered: Boolean
            try {
                triggered = trigger.trigger(cmd)
            } catch (e: Exception) {
                OutputTuils.sendOutput(mContext, LogTuils.getStackTrace(e))
                break
            }
            if (triggered) {
                messagesManager?.afterCmd()
                break
            }
        }
    }

    fun onLongBack() {
        OutputTuils.sendInput(mContext, Tuils.EMPTYSTRING)
    }

    fun dispose() {
        mainPack.dispose()
    }

    fun destroy() {
        appsManager.onDestroy()
        contactManager?.destroy(mContext)

        TuiLocationManager.disposeStatic()

        messagesManager?.onDestroy()

        themeManager.dispose()
        htmlExtractManager.dispose(mContext)
        aliasManager.dispose()
        LocalBroadcastManager.getInstance(mContext.getApplicationContext())
            .unregisterReceiver(receiver)

        object : StoppableThread() {
            override fun run() {
                super.run()

                try {
                    interactive.kill()
                    interactive.close()
                } catch (e: Exception) {
                    LogTuils.log(e)
                    // LogTuils.toFile(e);
                }
            }
        }.start()
    }

    fun executer(): CommandExecuter {
        return CommandExecuter { input: String?, obj: Any? ->
            val launchable = if (obj is Launchable) obj else null
            onCommand(input ?: return@CommandExecuter, launchable)
        }
    }

    //
    var pa: Pattern = Pattern.compile("%a", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    var pp: Pattern = Pattern.compile("%p", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)
    var pl: Pattern = Pattern.compile("%l", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

    init {
        keeperServiceRunning = XMLPrefsManager.getBoolean(Behavior.tui_notification)

        showAliasValue = XMLPrefsManager.getBoolean(Behavior.show_alias_content)
        aliasContentColor = XMLPrefsManager.getColor(Theme.alias_content_color)

        try {
            contactManager = ContactManager(mContext)
        } catch (e: NullPointerException) {
            LogTuils.log(e)
        }

        appsManager = AppsManager(mContext)
        aliasManager = AliasManager(mContext)

        val client = OkHttpClient.Builder()
            .cache(Cache(mContext.getCacheDir(), (10 * 1024 * 1024).toLong()))
            .build()

        themeManager = ThemeManager(client, mContext, mContext)
        ChangelogManager.printLog(mContext, client)
        htmlExtractManager = HTMLExtractManager(mContext, client)

        if (XMLPrefsManager.getBoolean(Behavior.show_hints)) {
            messagesManager = MessagesManager(mContext)
        }

        val shellHolder = ShellHolder(mContext)
        interactive = shellHolder.build()

        // Explicit registration (instead of classpath scanning) so each command can declare
        // constructor dependencies rather than pulling every manager out of a shared object.
        val group = CommandGroup(
            listOf(
                alias(aliasManager),
                beep(),
                bluetooth(),
                calc(),
                call(),
                changelog(client),
                clear(),
                cntcts(contactManager),
                config(),
                ctrlc(shellHolder),
                data(),
                devutils(),
                donate(),
                exit(),
                flash(),
                help(),
                htmlextract(),
                location(),
                notifications(),
                open(),
                rate(),
                refresh(aliasManager, appsManager, contactManager),
                regex(),
                reply(),
                restart(),
                search(),
                share(),
                shellcommands(),
                shortcut(appsManager),
                status(),
                theme(),
                time(),
                timer(),
                tui(),
                tuiweather(),
                tutorial(),
                uninstall(),
                vibrate(),
                wacall(),
                apps(appsManager)
            )
        )

        mainPack = CommandContext(mContext, group)

        val filter = IntentFilter()
        filter.addAction(ACTION_EXEC)
        filter.addAction(location.ACTION_LOCATION_CMD_GOT)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.getAction()
                if (action == ACTION_EXEC) {
                    var cmd = intent.getStringExtra(CMD)
                    if (cmd == null) cmd = intent.getStringExtra(PrivateIOReceiver.TEXT)

                    if (cmd == null) {
                        return
                    }

                    val cmdCount = intent.getIntExtra(CMD_COUNT, -1)
                    if (cmdCount < commandCount) return
                    commandCount++

                    val aliasName = intent.getStringExtra(ALIAS_NAME)
                    val needWriteInput = intent.getBooleanExtra(NEED_WRITE_INPUT, false)
                    val p = intent.getParcelableExtra<Parcelable?>(PARCELABLE)

                    if (needWriteInput) {
                        val i = Intent(PrivateIOReceiver.ACTION_INPUT)
                        i.putExtra(PrivateIOReceiver.TEXT, cmd)
                        LocalBroadcastManager.getInstance(context.getApplicationContext())
                            .sendBroadcast(i)
                    }

                    if (p != null && p is Launchable) {
                        onCommand(cmd, p)
                    } else {
                        onCommand(cmd, aliasName)
                    }
                } else if (action == location.ACTION_LOCATION_CMD_GOT) {
                    OutputTuils.sendOutput(
                        context,
                        "Lat: " + intent.getDoubleExtra(
                            TuiLocationManager.LATITUDE,
                            0.0
                        ) + "; Long: " + intent.getDoubleExtra(TuiLocationManager.LONGITUDE, 0.0)
                    )
                    TuiLocationManager.instance(context).rm(location.ACTION_LOCATION_CMD_GOT)
                }
            }
        }

        LocalBroadcastManager.getInstance(mContext.getApplicationContext())
            .registerReceiver(receiver, filter)
    }

    //
    //
    interface CmdTrigger {
        @Throws(Exception::class)
        fun trigger(input: String): Boolean
    }

    private inner class AliasTrigger : CmdTrigger {
        override fun trigger(input: String): Boolean {
            val alias = aliasManager.getAlias(input, true) ?: return false
            val aliasValue = aliasManager.format(alias[0] ?: return false, alias[2].orEmpty())
            onCommand(aliasValue, alias[1])
            return true
        }
    }

    private inner class GroupTrigger : CmdTrigger {

        @Throws(Exception::class)
        override fun trigger(input: String): Boolean {
            val index = input.indexOf(Tuils.SPACE)
            val name: String
            val rest: String?

            if (index != -1) {
                name = input.substring(0, index)
                rest = input.substring(index + 1)
            } else {
                name = input
                rest = null
            }

            val appGroups = appsManager.groups
            for (g in appGroups) {
                if (name == g.name()) {
                    if (rest == null) {
                        OutputTuils.sendOutput(
                            mContext,
                            printApps(Launchable.labelList(g.members() as MutableList<out Launchable>, false))
                        )
                        return true
                    } else {
                        return g.use(mainPack, rest)
                    }
                }
            }
            return false
        }
    }

    private inner class ShellCommandTrigger : CmdTrigger {
        val CD_CODE: Int = 10
        val PWD_CODE: Int = 11

        val result: OnCommandResultListener = object : OnCommandResultListener {
            override fun onCommandResult(
                commandCode: Int,
                exitCode: Int,
                output: MutableList<String?>
            ) {
                if (commandCode == CD_CODE) {
                    interactive.addCommand("pwd", PWD_CODE, result)
                } else if (commandCode == PWD_CODE && output.size == 1) {
                    val f = File(output.get(0))
                    if (f.exists()) {
                        mainPack.currentDirectory = f

                        LocalBroadcastManager.getInstance(mContext.getApplicationContext())
                            .sendBroadcast(
                                Intent(
                                    UIManager.ACTION_UPDATE_HINT
                                )
                            )
                    }
                }
            }
        }

        @Throws(Exception::class)
        override fun trigger(input: String): Boolean {
            object : StoppableThread() {
                override fun run() {
                    if (input.trim { it <= ' ' }.equals("su", ignoreCase = true)) {
                        if (Shell.SU.available()) LocalBroadcastManager.getInstance(mContext.getApplicationContext())
                            .sendBroadcast(Intent(UIManager.ACTION_ROOT))
                        interactive.addCommand("su")
                    } else if (input.contains("cd ")) {
                        interactive.addCommand(input, CD_CODE, result)
                    } else {
                        interactive.addCommand(input)
                    }
                }
            }.start()
            return true
        }
    }

    private inner class AppTrigger : CmdTrigger {
        override fun trigger(input: String): Boolean {
            val launchable = appsManager.findLaunchableWithLabel(input)
            if (launchable != null) return appsManager.performLaunch(launchable)

            val spaceIdx = input.indexOf(' ')
            if (spaceIdx != -1) {
                val web = appsManager.findLaunchableWithLabel(input.substring(0, spaceIdx)) as? WebLauncher
                if (web != null && web.url.contains("%s")) {
                    web.launchWithQuery(mContext, input.substring(spaceIdx + 1).trim())
                    return true
                }
            }
            return false
        }
    }

    private inner class TuiCommandTrigger : CmdTrigger {
        override fun trigger(input: String): Boolean {
            val command = CommandParser.parse(input, mainPack, appsManager, contactManager)
            if (command == null) return false
            mainPack.lastCommand = input
            object : StoppableThread() {
                override fun run() {
                    super.run()
                    try {
                        when (val result = command.execResult(mainPack)) {
                            is CommandResult.Output -> OutputTuils.sendOutput(
                                mainPack.context,
                                result.text,
                                if (result.color != Integer.MAX_VALUE) result.color else mainPack.commandColor,
                                TerminalManager.CATEGORY_OUTPUT
                            )
                            is CommandResult.Error -> OutputTuils.sendOutput(
                                mainPack.context,
                                result.text,
                                Color.RED,
                                TerminalManager.CATEGORY_OUTPUT
                            )
                            CommandResult.Empty -> {}
                        }
                    } catch (e: Exception) {
                        OutputTuils.sendOutput(mContext, LogTuils.getStackTrace(e))
                        LogTuils.log(e)
                    }
                }
            }.start()
            return true
        }
    }

    interface Group {
        fun members(): MutableList<out Any?>?
        fun use(mainPack: CommandContext?, input: String?): Boolean
        fun name(): String?
    }

    companion object {
        @JvmField
        var ACTION_EXEC: String = BuildConfig.APPLICATION_ID + ".main_exec"
        @JvmField
        var CMD: String = "cmd"
        @JvmField
        var NEED_WRITE_INPUT: String = "writeInput"
        var ALIAS_NAME: String = "aliasName"
        var PARCELABLE: String = "parcelable"
        @JvmField
        var CMD_COUNT: String = "cmdCount"

        lateinit var interactive: Interactive

        @JvmField
        var commandCount: Int = 0
    }
}
