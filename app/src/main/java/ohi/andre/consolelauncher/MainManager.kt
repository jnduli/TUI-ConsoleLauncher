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
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandTuils
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.raw.location
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppUtils
import ohi.andre.consolelauncher.managers.AppUtils.printApps
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.WebLauncher
import ohi.andre.consolelauncher.managers.ChangelogManager
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.Launchable
import ohi.andre.consolelauncher.managers.MessagesManager
import ohi.andre.consolelauncher.managers.RssManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.ThemeManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.TuiLocationManager
import ohi.andre.consolelauncher.managers.music.MusicManager2
import ohi.andre.consolelauncher.managers.music.MusicService
import ohi.andre.consolelauncher.managers.notifications.KeeperService
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import ohi.andre.consolelauncher.tuils.interfaces.OnRedirectionListener
import ohi.andre.consolelauncher.tuils.interfaces.Redirectator
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
    private var redirect: RedirectCommand? = null
    private val redirectator: Redirectator = object : Redirectator {
        override fun prepareRedirection(cmd: RedirectCommand?) {
            redirect = cmd
            redirectionListener?.onRedirectionRequest(cmd)
        }

        override fun cleanup() {
            redirect?.let {
                it.beforeObjects.clear()
                it.afterObjects.clear()
                redirectionListener?.onRedirectionEnd(it)
                redirect = null
            }
        }
    }
    private var redirectionListener: OnRedirectionListener? = null
    fun setRedirectionListener(redirectionListener: OnRedirectionListener?) {
        this.redirectionListener = redirectionListener
    }

    private val COMMANDS_PKG = "ohi.andre.consolelauncher.commands.main.raw"

    private val triggers = arrayOf<CmdTrigger>(
        GroupTrigger(),
        AliasTrigger(),
        TuiCommandTrigger(),
        AppTrigger(),
        ShellCommandTrigger()
    )
    val mainPack: MainPack

    private val showAliasValue: Boolean
    private val aliasContentColor: Int

    private val multipleCmdSeparator: String

    private val aliasManager: AliasManager
    private val rssManager: RssManager?
    private val appsManager: AppsManager
    private var contactManager: ContactManager? = null
    private val musicManager2: MusicManager2?
    private val themeManager: ThemeManager
    private val htmlExtractManager: HTMLExtractManager

    var messagesManager: MessagesManager? = null

    private val receiver: BroadcastReceiver

    private val keeperServiceRunning: Boolean

    private fun updateServices(cmd: String?, wasMusicService: Boolean) {
        if (keeperServiceRunning) {
            val i = Intent(mContext, KeeperService::class.java)
            i.putExtra(KeeperService.CMD_KEY, cmd)
            i.putExtra(KeeperService.PATH_KEY, mainPack.currentDirectory.getAbsolutePath())
            mContext.startService(i)
        }

        if (wasMusicService) {
            val i = Intent(mContext, MusicService::class.java)
            mContext.startService(i)
        }
    }

    fun onCommand(input: String, launchable: Launchable?, wasMusicService: Boolean) {
        if (launchable == null) {
            onCommand(input, null as String?, wasMusicService)
            return
        }

        updateServices(input, wasMusicService)

        val unspacedLabel = Tuils.removeSpaces(launchable.name().lowercase(Locale.getDefault()))
        if (unspacedLabel == Tuils.removeSpaces(input.lowercase(Locale.getDefault()))) {
            appsManager.performLaunch(launchable)
        } else {
            onCommand(input, null as String?, wasMusicService)
        }
    }

    var colorExtractor: Pattern =
        Pattern.compile("(#[^(]{6})\\[([^\\)]*)\\]", Pattern.CASE_INSENSITIVE)

    fun onCommand(input: String?, alias: String?, wasMusicService: Boolean) {
        val input = Tuils.removeUnncesarySpaces(input)

        if (alias == null) updateServices(input, wasMusicService)

        val currentRedirect = redirect
        if (currentRedirect != null) {
            if (!currentRedirect.isWaitingPermission()) {
                currentRedirect.afterObjects.add(input)
            }
            Tuils.sendOutput(mContext, currentRedirect.onRedirect(mainPack))
            return
        }

        if (alias != null && showAliasValue) {
            Tuils.sendOutput(aliasContentColor, mContext, aliasManager.formatLabel(alias, input))
        }

        val (cmds, colors) = splitAndColorize(input ?: return)
        for (c in cmds.indices) {
            executeCmd(cmds[c], colors[c])
        }
    }

    private fun splitAndColorize(input: String): Pair<Array<String?>, IntArray> {
        val cmds: Array<String?> = if (multipleCmdSeparator.isNotEmpty()) {
            input.split(multipleCmdSeparator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        } else {
            arrayOf(input)
        }
        val colors = IntArray(cmds.size)
        for (c in cmds.indices) {
            val m = colorExtractor.matcher(cmds[c])
            if (m.matches()) {
                try {
                    colors[c] = Color.parseColor(m.group(1))
                    cmds[c] = m.group(2)
                } catch (e: Exception) {
                    colors[c] = TerminalManager.NO_COLOR
                }
            } else {
                colors[c] = TerminalManager.NO_COLOR
            }
        }
        return Pair(cmds, colors)
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
                Tuils.sendOutput(mContext, Tuils.getStackTrace(e))
                break
            }
            if (triggered) {
                messagesManager?.afterCmd()
                break
            }
        }
    }

    fun onLongBack() {
        Tuils.sendInput(mContext, Tuils.EMPTYSTRING)
    }

    fun sendPermissionNotGrantedWarning() {
        redirectator.cleanup()
    }

    fun dispose() {
        mainPack.dispose()
    }

    fun destroy() {
        mainPack.destroy()
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
                    Tuils.log(e)
                    // Tuils.toFile(e);
                }
            }
        }.start()
    }

    fun executer(): CommandExecuter {
        return CommandExecuter { input: String?, obj: Any? ->
            val launchable = if (obj is Launchable) obj else null
            onCommand(input!!, launchable, false)
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

        multipleCmdSeparator = XMLPrefsManager.get(Behavior.multiple_cmd_separator)

        val group = CommandGroup(mContext, COMMANDS_PKG)

        try {
            contactManager = ContactManager(mContext)
        } catch (e: NullPointerException) {
            Tuils.log(e)
        }

        appsManager = AppsManager(mContext)
        aliasManager = AliasManager(mContext)

        val client = OkHttpClient.Builder()
            .cache(Cache(mContext.getCacheDir(), (10 * 1024 * 1024).toLong()))
            .build()

        rssManager = RssManager(mContext, client)
        themeManager = ThemeManager(client, mContext, mContext)
        musicManager2 =
            if (XMLPrefsManager.getBoolean(Behavior.enable_music)) MusicManager2(mContext) else null
        ChangelogManager.printLog(mContext, client)
        htmlExtractManager = HTMLExtractManager(mContext, client)

        if (XMLPrefsManager.getBoolean(Behavior.show_hints)) {
            messagesManager = MessagesManager(mContext)
        }

        mainPack = MainPack(
            mContext,
            group,
            aliasManager,
            appsManager,
            musicManager2,
            contactManager,
            redirectator,
            rssManager,
            client
        )

        val shellHolder = ShellHolder(mContext)
        interactive = shellHolder.build()
        mainPack.shellHolder = shellHolder

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
                        onCommand(cmd, p, intent.getBooleanExtra(MUSIC_SERVICE, false))
                    } else {
                        onCommand(cmd, aliasName, intent.getBooleanExtra(MUSIC_SERVICE, false))
                    }
                } else if (action == location.ACTION_LOCATION_CMD_GOT) {
                    Tuils.sendOutput(
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
            val aliasValue = aliasManager.format(alias[0] ?: return false, alias[2])
            onCommand(aliasValue, alias[1], false)
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

            val appGroups = mainPack.appsManager?.groups ?: return false
            for (g in appGroups) {
                if (name == g.name()) {
                    if (rest == null) {
                        Tuils.sendOutput(
                            mContext,
                            printApps(AppUtils.labelList(g.members() as MutableList<out Launchable>, false))
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
            val command = CommandTuils.parse(input, mainPack)
            if (command == null) return false
            mainPack.lastCommand = input
            object : StoppableThread() {
                override fun run() {
                    super.run()
                    try {
                        val output = command.exec(mainPack)
                        if (output != null) {
                            Tuils.sendOutput(mainPack, output, TerminalManager.CATEGORY_OUTPUT)
                        }
                    } catch (e: Exception) {
                        Tuils.sendOutput(mContext, Tuils.getStackTrace(e))
                        Tuils.log(e)
                    }
                }
            }.start()
            return true
        }
    }

    interface Group {
        fun members(): MutableList<out Any?>?
        fun use(mainPack: MainPack?, input: String?): Boolean
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
        @JvmField
        var MUSIC_SERVICE: String = "musicService"

        lateinit var interactive: Interactive

        @JvmField
        var commandCount: Int = 0
    }
}
