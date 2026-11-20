package ohi.andre.consolelauncher

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.compose.TerminalScreen
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.SystemUtils
import ohi.andre.consolelauncher.tuils.FileTuils
import ohi.andre.consolelauncher.tuils.OutputTuils
import ohi.andre.consolelauncher.tuils.ViewTuils

import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import ohi.andre.consolelauncher.tuils.stuff.PolicyReceiver
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern
import kotlin.math.min

class UIManager(
    context: Context,
    rootView: ViewGroup,
    mainPack: CommandContext?,
    canApplyTheme: Boolean,
    executer: CommandExecuter?,
    aliasManager: ohi.andre.consolelauncher.managers.AliasManager,
    appsManager: ohi.andre.consolelauncher.managers.AppsManager,
    contacts: ohi.andre.consolelauncher.managers.ContactManager?,
) {
    public lateinit var mContext: Context

    private var policy: DevicePolicyManager?
    private var component: ComponentName?

    var preferences: SharedPreferences

    private val mTerminalAdapter: TerminalManager
    private val suggestionsManager: SuggestionsManager?

    private var doubleTapCmd: String?
    private var lockOnDbTap: Boolean

    private val receiver: BroadcastReceiver

    @kotlin.jvm.JvmField
    var pack: CommandContext? = null

    fun dispose() {
        mTerminalAdapter.dispose()
        suggestionsManager?.dispose()

        LocalBroadcastManager.getInstance(mContext.applicationContext)
            .unregisterReceiver(receiver)

        ViewTuils.cancelFont()
    }

    fun openKeyboard() {
        mTerminalAdapter.showKeyboard()
    }

    fun closeKeyboard() {
        mTerminalAdapter.hideKeyboard()
    }

    fun onStart(openKeyboardOnStart: Boolean) {
        if (openKeyboardOnStart) openKeyboard()
    }

    fun setInput(s: String?) {
        if (s == null) return

        mTerminalAdapter.input = s
        mTerminalAdapter.focusInputEnd()
    }

    fun setHint(hint: String?) {
        mTerminalAdapter.setHint(hint)
    }

    fun resetHint() {
        mTerminalAdapter.setDefaultHint()
    }

    fun setOutput(s: CharSequence?, category: Int) {
        mTerminalAdapter.setOutput(s, category)
    }

    fun setOutput(color: Int, output: CharSequence?) {
        mTerminalAdapter.setOutput(color, output)
    }

    fun onBackPressed() {
        mTerminalAdapter.onBackPressed()
    }

    fun focusTerminal() {
        mTerminalAdapter.requestInputFocus()
    }

    fun pause() {
        closeKeyboard()
    }

    private fun handleDoubleTapOutput() {
        if (doubleTapCmd?.isNotEmpty() == true) {
            val previousInput = mTerminalAdapter.input
            mTerminalAdapter.input = doubleTapCmd as String
            mTerminalAdapter.simulateEnter()
            mTerminalAdapter.input = previousInput
        }

        if (lockOnDbTap) {
            val pol = policy
            val comp = component
            if (pol != null && comp != null) {
                if (!pol.isAdminActive(comp)) {
                    val i = SystemUtils.requestAdmin(comp, mContext.getString(R.string.admin_permission))
                    mContext.startActivity(i)
                } else {
                    pol.lockNow()
                }
            }
        }
    }

    init {
        val filter = IntentFilter()
        filter.addAction(ACTION_UPDATE_SUGGESTIONS)
        filter.addAction(ACTION_UPDATE_HINT)
        filter.addAction(ACTION_ROOT)
        filter.addAction(ACTION_NOROOT)
        filter.addAction(ACTION_LOGTOFILE)
        filter.addAction(ACTION_CLEAR)
        filter.addAction(ACTION_WEATHER)
        filter.addAction(ACTION_WEATHER_GOT_LOCATION)
        filter.addAction(ACTION_WEATHER_DELAY)
        filter.addAction(ACTION_WEATHER_MANUAL_UPDATE)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action

                if (action == ACTION_UPDATE_SUGGESTIONS) {
                    suggestionsManager?.requestSuggestion(Tuils.EMPTYSTRING)
                } else if (action == ACTION_UPDATE_HINT) {
                    mTerminalAdapter.setDefaultHint()
                } else if (action == ACTION_ROOT) {
                    mTerminalAdapter.onRoot()
                } else if (action == ACTION_NOROOT) {
                    mTerminalAdapter.onStandard()
                } else if (action == ACTION_LOGTOFILE) {
                    val fileName = intent.getStringExtra(FILE_NAME)
                    if (fileName == null || fileName.contains(File.separator)) return

                    val file = File(FileTuils.getFolder(context), fileName)
                    if (file.exists()) file.delete()

                    try {
                        file.createNewFile()
                        val fos = FileOutputStream(file)
                        fos.write(mTerminalAdapter.getTerminalText().toByteArray())
                        OutputTuils.sendOutput(context, "Logged to " + file.absolutePath)
                    } catch (e: Exception) {
                        Log.e(TAG, e.toString())
                        OutputTuils.sendOutput(context, e.toString(), Color.RED)
                    }
                } else if (action == ACTION_CLEAR) {
                    mTerminalAdapter.clear()
                    suggestionsManager?.requestSuggestion(Tuils.EMPTYSTRING)
                }
            }
        }

        LocalBroadcastManager.getInstance(context.applicationContext)
            .registerReceiver(receiver, filter)

        policy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
        component = ComponentName(context, PolicyReceiver::class.java)

        mContext = context

        preferences = mContext.getSharedPreferences(PREFS_NAME, 0)

        if (!XMLPrefsManager.getBoolean(Ui.system_wallpaper) || !canApplyTheme) {
            rootView.setBackgroundColor(XMLPrefsManager.getColor(Theme.bg_color))
        } else {
            rootView.setBackgroundColor(XMLPrefsManager.getColor(Theme.overlay_color))
        }

        if (XMLPrefsManager.getBoolean(Behavior.auto_scroll)) {
            rootView.viewTreeObserver.addOnGlobalLayoutListener(OnGlobalLayoutListener {
                val heightDiff = rootView.rootView.height - rootView.height
                if (heightDiff > ViewTuils.dpToPx(context, 200)) { // if more than 200 dp, it's probably a keyboard...
                    mTerminalAdapter.scrollToEnd()
                }
            })
        }

        lockOnDbTap = XMLPrefsManager.getBoolean(Behavior.double_tap_lock)
        doubleTapCmd = XMLPrefsManager.get(Behavior.double_tap_cmd) as String?
        if (!lockOnDbTap && doubleTapCmd == null) {
            policy = null
            component = null
        }

        val displayMargins: IntArray =
            getListOfIntValues(XMLPrefsManager.get(Ui.display_margin_mm), 4, 0)
        val metrics = mContext.resources.displayMetrics
        rootView.setPadding(
            ViewTuils.mmToPx(metrics, displayMargins[0]),
            ViewTuils.mmToPx(metrics, displayMargins[1]),
            ViewTuils.mmToPx(metrics, displayMargins[2]),
            ViewTuils.mmToPx(metrics, displayMargins[3])
        )

        mTerminalAdapter = TerminalManager(context, requireNotNull(mainPack), requireNotNull(executer))

        suggestionsManager = if (XMLPrefsManager.getBoolean(Suggestions.show_suggestions)) {
            SuggestionsManager(requireNotNull(mainPack), mTerminalAdapter, aliasManager, appsManager, contacts)
        } else {
            null
        }

        val inputAtBottom = XMLPrefsManager.getBoolean(Ui.input_bottom)
        val showToolbar = XMLPrefsManager.getBoolean(Toolbar.show_toolbar)
        val hideToolbarNoInput = showToolbar && XMLPrefsManager.getBoolean(Toolbar.hide_toolbar_no_input)
        val showSubmitButton = XMLPrefsManager.getBoolean(Ui.show_enter_button)

        val composeView = ComposeView(mContext)
        rootView.addView(composeView)
        setupTerminalCompose(
            composeView, mTerminalAdapter, suggestionsManager,
            inputAtBottom, showToolbar, hideToolbarNoInput, showSubmitButton,
        )
    }

    // Kept as a regular member function (not inlined into the constructor above) because the Kotlin/
    // Compose compiler fails to generate code for a composable lambda that captures a local val
    // declared directly inside a constructor/init block ("No mapping for symbol" IR codegen crash).
    private fun setupTerminalCompose(
        composeView: ComposeView,
        terminalManager: TerminalManager,
        suggMgr: SuggestionsManager?,
        inputAtBottom: Boolean,
        showToolbar: Boolean,
        hideToolbarNoInput: Boolean,
        showSubmitButton: Boolean,
    ) {
        composeView.setContent {
            TerminalScreen(
                terminalManager = terminalManager,
                suggestionsManager = suggMgr,
                inputAtBottom = inputAtBottom,
                showToolbar = showToolbar,
                hideToolbarNoInput = hideToolbarNoInput,
                showSubmitButton = showSubmitButton,
                onDoubleTapOutput = { handleDoubleTapOutput() },
                modifier = Modifier,
            )
        }
    }

    companion object {

        private const val TAG = "UIManager"
        @kotlin.jvm.JvmField
        var ACTION_UPDATE_SUGGESTIONS: kotlin.String =
            BuildConfig.APPLICATION_ID + ".ui_update_suggestions"
        @kotlin.jvm.JvmField
        var ACTION_UPDATE_HINT: kotlin.String = BuildConfig.APPLICATION_ID + ".ui_update_hint"
        @kotlin.jvm.JvmField
        var ACTION_ROOT: kotlin.String = BuildConfig.APPLICATION_ID + ".ui_root"
        @kotlin.jvm.JvmField
        var ACTION_NOROOT: kotlin.String = BuildConfig.APPLICATION_ID + ".ui_noroot"
        @kotlin.jvm.JvmField
        var ACTION_LOGTOFILE: kotlin.String = BuildConfig.APPLICATION_ID + ".ui_log"
        @kotlin.jvm.JvmField
        var ACTION_CLEAR: kotlin.String = BuildConfig.APPLICATION_ID + "ui_clear"
        @kotlin.jvm.JvmField
        var ACTION_WEATHER: kotlin.String = BuildConfig.APPLICATION_ID + "ui_weather"
        var ACTION_WEATHER_GOT_LOCATION: kotlin.String =
            BuildConfig.APPLICATION_ID + "ui_weather_location"
        @kotlin.jvm.JvmField
        var ACTION_WEATHER_DELAY: kotlin.String = BuildConfig.APPLICATION_ID + "ui_weather_delay"
        @kotlin.jvm.JvmField
        var ACTION_WEATHER_MANUAL_UPDATE: kotlin.String =
            BuildConfig.APPLICATION_ID + "ui_weather_update"

        @kotlin.jvm.JvmField
        var FILE_NAME: kotlin.String = "fileName"
        @kotlin.jvm.JvmField
        var PREFS_NAME: kotlin.String = "ui"

        @kotlin.jvm.JvmStatic
        fun getListOfIntValues(values: kotlin.String, length: Int, defaultValue: Int): IntArray {
            var values = values
            val `is` = IntArray(length)
            values = removeSquareBrackets(values)
            val split: Array<kotlin.String?> =
                values.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var c = 0
            while (c < split.size) {
                `is`[c] = split[c]?.toIntOrNull() ?: defaultValue
                c++
            }
            while (c < split.size) `is`[c] = defaultValue

            return `is`
        }

        fun getListOfStringValues(
            values: kotlin.String,
            length: Int,
            defaultValue: kotlin.String?
        ): Array<kotlin.String?> {
            val `is` = arrayOfNulls<kotlin.String>(length)
            val split: Array<kotlin.String?> =
                values.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            var len: Int = min(split.size, `is`.size)
            System.arraycopy(split, 0, `is`, 0, len)

            while (len < `is`.size) `is`[len++] = defaultValue

            return `is`
        }

        private val sbPattern: Pattern = Pattern.compile("[\\[\\]\\s]")
        private fun removeSquareBrackets(s: kotlin.String): kotlin.String {
            return sbPattern.matcher(s).replaceAll(Tuils.EMPTYSTRING)
        }

        @kotlin.jvm.JvmField
        var UNLOCK_KEY: kotlin.String = "unlockTimes"
        @kotlin.jvm.JvmField
        var NEXT_UNLOCK_CYCLE_RESTART: kotlin.String = "nextUnlockRestart"
    }
}
