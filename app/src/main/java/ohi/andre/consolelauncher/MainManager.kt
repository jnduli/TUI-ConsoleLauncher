package ohi.andre.consolelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Parcelable
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.cli.ActionDispatcher
import ohi.andre.consolelauncher.cli.alias.AliasResolver
import ohi.andre.consolelauncher.cli.builtin.TuiBuiltinResolver
import ohi.andre.consolelauncher.cli.group.GroupResolver
import ohi.andre.consolelauncher.cli.launchable.LaunchAction
import ohi.andre.consolelauncher.cli.launchable.LaunchableResolver
import ohi.andre.consolelauncher.cli.shell.ShellResolver
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandParser
import ohi.andre.consolelauncher.commands.main.raw.*
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.ChangelogManager
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.launchable.Launchable
import ohi.andre.consolelauncher.managers.MessagesManager
import ohi.andre.consolelauncher.managers.ThemeManager
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
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell.Interactive
import ohi.andre.consolelauncher.tuils.libsuperuser.ShellHolder
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.Locale
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
    val mainPack: CommandContext
    private val dispatcher: ActionDispatcher

    val aliasManager: AliasManager
    val appsManager: AppsManager
    val contactManager: ContactManager?
    val parser: CommandParser
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

    /**
     * Input with a tapped suggestion attached: launches exactly that [tapped] entry if the text
     * still names it (the user may have edited the text after tapping).
     */
    fun onCommand(input: String, tapped: Launchable? = null) {
        updateServices(StringTuils.removeUnncesarySpaces(input))
        val unspaced = { str: String -> StringTuils.removeSpaces(str.lowercase(Locale.getDefault())) }
        if (tapped != null && unspaced(tapped.name()) == unspaced(input)) {
            dispatcher.submit(LaunchAction(appsManager, mContext, tapped), input)
        } else {
            dispatcher.submit(input)
        }
        messagesManager?.afterCmd()
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

        contactManager = try {
            ContactManager(mContext)
        } catch (e: NullPointerException) {
            LogTuils.log(e)
            null
        }

        appsManager = AppsManager(mContext)
        parser = CommandParser(appsManager, contactManager)
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

        val aliasEchoColor =
            if (XMLPrefsManager.getBoolean(Behavior.show_alias_content)) XMLPrefsManager.getColor(Theme.alias_content_color)
            else null

        dispatcher = ActionDispatcher(
            mainPack,
        ) { reenter ->
            val builtins = TuiBuiltinResolver(mainPack, parser)
            // Order matters: the first resolver that claims the input wins, and the shell is the
            // catch-all.
            listOf(
                GroupResolver(appsManager, mContext, builtins),
                AliasResolver(aliasManager, mContext, aliasEchoColor, reenter),
                builtins,
                LaunchableResolver(appsManager, mContext),
                ShellResolver(mainPack),
            )
        }

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

                    val needWriteInput = intent.getBooleanExtra(NEED_WRITE_INPUT, false)
                    val p = intent.getParcelableExtra<Parcelable?>(PARCELABLE)

                    if (needWriteInput) {
                        val i = Intent(PrivateIOReceiver.ACTION_INPUT)
                        i.putExtra(PrivateIOReceiver.TEXT, cmd)
                        LocalBroadcastManager.getInstance(context.getApplicationContext())
                            .sendBroadcast(i)
                    }

                    onCommand(cmd, p as? Launchable)
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
        var PARCELABLE: String = "parcelable"
        @JvmField
        var CMD_COUNT: String = "cmdCount"

        lateinit var interactive: Interactive

        @JvmField
        var commandCount: Int = 0
    }
}
