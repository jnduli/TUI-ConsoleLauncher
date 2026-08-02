package ohi.andre.consolelauncher.managers

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.launchable.Launchable
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter

/** One-shot UI actions TerminalManager can't perform itself (no View/composition to act on) -
 * the composable hosting the terminal subscribes to [TerminalManager.events] and performs them. */
sealed interface TerminalUiEvent {
    data object RequestFocus : TerminalUiEvent
    data object ShowKeyboard : TerminalUiEvent
    data object HideKeyboard : TerminalUiEvent
    data object ScrollToEnd : TerminalUiEvent
}

class TerminalManager(
    val mContext: Context,
    private val mainPack: MainPack,
    private val executer: CommandExecuter,
) {
    companion object {
        const val CATEGORY_INPUT = 10
        const val CATEGORY_OUTPUT = 11
        const val CATEGORY_NO_COLOR = 20
        const val NO_COLOR = Int.MAX_VALUE

        const val FORMAT_INPUT = "%i"
        const val FORMAT_OUTPUT = "%o"
        const val FORMAT_PREFIX = "%p"
        const val FORMAT_NEWLINE = "%n"

        private const val CMD_LIST_SIZE = 40
        private const val SCROLL_DELAY = 200L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _events = MutableSharedFlow<TerminalUiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TerminalUiEvent> = _events.asSharedFlow()

    private val _output = MutableStateFlow<CharSequence>(Tuils.EMPTYSTRING)
    val output: StateFlow<CharSequence> = _output.asStateFlow()

    private val _inputValue = MutableStateFlow(TextFieldValue(Tuils.EMPTYSTRING))
    val inputValue: StateFlow<TextFieldValue> = _inputValue.asStateFlow()
    private var inputObject: Any? = null

    private val _prefix = MutableStateFlow(Tuils.EMPTYSTRING)
    val prefix: StateFlow<String> = _prefix.asStateFlow()

    private val _hint = MutableStateFlow(Tuils.EMPTYSTRING)
    val hint: StateFlow<String> = _hint.asStateFlow()

    private var defaultHint = true
    private var suMode = false

    private var lastEnter = 0L
    private val cmdList = ArrayList<String>(CMD_LIST_SIZE)
    private var howBack = -1

    private var clearCmdsCount = 0
    private val clearAfterCmds: Int = XMLPrefsManager.getInt(Behavior.clear_after_cmds)
    private val maxLines: Int = XMLPrefsManager.getInt(Behavior.max_lines)

    private val inputFormat: String = XMLPrefsManager.get(Behavior.input_format)
    private val outputFormat: String = XMLPrefsManager.get(Behavior.output_format)
    private val inputColor: Int = XMLPrefsManager.getColor(Theme.input_color)
    private val outputColor: Int = XMLPrefsManager.getColor(Theme.output_color)

    private val prefixText: String = XMLPrefsManager.get(Ui.input_prefix)
    private val suPrefixText: String = XMLPrefsManager.get(Ui.input_root_prefix)

    private val clickCommands: Boolean = XMLPrefsManager.getBoolean(Behavior.click_commands)
    private val longClickCommands: Boolean = XMLPrefsManager.getBoolean(Behavior.long_click_commands)

    var input: String
        get() = _inputValue.value.text
        set(value) = applyInput(value, null)

    init {
        _prefix.value = withTrailingSpace(prefixText)
        _hint.value = Tuils.getHint(mainPack.currentDirectory.absolutePath) ?: Tuils.EMPTYSTRING

        val clearAfterMs = XMLPrefsManager.getInt(Behavior.clear_after_seconds) * 1000
        if (clearAfterMs > 0) {
            scope.launch {
                while (isActive) {
                    delay(clearAfterMs.toLong())
                    clear()
                }
            }
        }
    }

    private fun withTrailingSpace(s: String) = if (s.endsWith(Tuils.SPACE)) s else s + Tuils.SPACE

    fun dispose() {
        scope.cancel()
    }

    fun setInput(value: String, obj: Any?) = applyInput(value, obj)

    private fun applyInput(value: String, obj: Any?) {
        inputObject = obj
        _inputValue.value = TextFieldValue(value, TextRange(value.length))
    }

    /** Called by the composable's TextField onValueChange - a user-driven edit invalidates
     * whatever object a previous suggestion click attached to the input (matches the legacy
     * Spannable-based behavior, where edits could invalidate the attached span). */
    fun onInputEdited(value: TextFieldValue) {
        if (value.text != _inputValue.value.text) inputObject = null
        _inputValue.value = value
    }

    fun focusInputEnd() {
        val current = _inputValue.value
        _inputValue.value = current.copy(selection = TextRange(current.text.length))
    }

    fun setHint(hint: String?) {
        defaultHint = false
        _hint.value = hint.orEmpty()
    }

    fun setDefaultHint() {
        defaultHint = true
        _hint.value = Tuils.getHint(mainPack.currentDirectory.absolutePath) ?: Tuils.EMPTYSTRING
    }

    fun requestInputFocus() {
        _events.tryEmit(TerminalUiEvent.RequestFocus)
    }

    fun showKeyboard() {
        requestInputFocus()
        _events.tryEmit(TerminalUiEvent.ShowKeyboard)
    }

    fun hideKeyboard() {
        _events.tryEmit(TerminalUiEvent.HideKeyboard)
    }

    fun scrollToEnd() {
        scope.launch {
            delay(SCROLL_DELAY)
            _events.emit(TerminalUiEvent.ScrollToEnd)
        }
    }

    fun onNewInput(): Boolean {
        val text = _inputValue.value.text
        val cmd = text.trim()
        val obj = inputObject.takeIf { it is Launchable }

        if (text.isNotEmpty()) {
            clearCmdsCount++
            if (clearAfterCmds > 0 && clearCmdsCount % clearAfterCmds == 0) clear()

            writeToView(text, CATEGORY_INPUT)

            if (cmdList.size == CMD_LIST_SIZE) cmdList.removeAt(0)
            cmdList.add(cmd)
            howBack = -1
        }

        executer.execute(cmd, obj)

        setupNewInput()
        return true
    }

    fun simulateEnter(): Boolean = onNewInput()

    private fun setupNewInput() {
        applyInput(Tuils.EMPTYSTRING, null)
        if (defaultHint) _hint.value = Tuils.getHint(mainPack.currentDirectory.absolutePath) ?: Tuils.EMPTYSTRING
        requestInputFocus()
    }

    fun setOutput(output: CharSequence?, type: Int) {
        if (output.isNullOrEmpty()) return
        writeToView(output, type)
    }

    fun setOutput(color: Int, output: CharSequence?) {
        if (output.isNullOrEmpty()) return

        val actualColor = if (color == NO_COLOR) outputColor else color
        val si = SpannableString(output)
        si.setSpan(ForegroundColorSpan(actualColor), 0, output.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        writeToView(TextUtils.concat(Tuils.NEWLINE, si))
    }

    fun getTerminalText(): String = _output.value.toString()

    fun onBackPressed() {
        if (cmdList.isNotEmpty()) {
            if (howBack == -1) howBack = cmdList.size
            else if (howBack == 0) return
            howBack--
            input = cmdList[howBack]
        }
    }

    fun onNextPressed() {
        if (howBack != -1 && howBack < cmdList.size) {
            howBack++
            input = if (howBack == cmdList.size) Tuils.EMPTYSTRING else cmdList[howBack]
        }
    }

    fun clear() {
        _output.value = Tuils.EMPTYSTRING
        cmdList.clear()
        clearCmdsCount = 0
    }

    fun onRoot() {
        suMode = true
        _prefix.value = withTrailingSpace(suPrefixText)
    }

    fun onStandard() {
        suMode = false
        _prefix.value = withTrailingSpace(prefixText)
    }

    private fun writeToView(text: CharSequence, type: Int) {
        val formatted = getFinalText(text, type) ?: return
        writeToView(TextUtils.concat(Tuils.NEWLINE, formatted))
    }

    private fun writeToView(text: CharSequence) {
        var combined: CharSequence = TextUtils.concat(_output.value, text)
        if (maxLines > 0) combined = trimToMaxLines(combined)
        _output.value = combined
        scrollToEnd()
    }

    /** Approximates the legacy Layout.getLineCount()-based trim (which counted *rendered*,
     * word-wrapped lines) by counting hard newlines instead - wrapped lines aren't known until
     * Compose lays the text out, after this point. */
    private fun trimToMaxLines(text: CharSequence): CharSequence {
        var t = text
        var lineCount = t.count { it == '\n' } + 1
        while (lineCount > maxLines) {
            val index = TextUtils.indexOf(t, '\n')
            if (index == -1) break
            t = t.subSequence(index + 1, t.length)
            lineCount--
        }
        return t
    }

    private fun getFinalText(t: CharSequence, type: Int): CharSequence? {
        return when (type) {
            CATEGORY_INPUT -> {
                val str = t.toString()
                val su = str.startsWith("su ") || suMode

                val si = Tuils.span(inputFormat, inputColor)
                if (clickCommands || longClickCommands) {
                    si.setSpan(
                        LongClickableSpan(
                            if (clickCommands) str else null,
                            if (longClickCommands) str else null,
                            PrivateIOReceiver.ACTION_INPUT,
                        ),
                        0, si.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }

                var s: CharSequence = TimeManager.instance.replace(si)
                s = TextUtils.replace(
                    s,
                    arrayOf(FORMAT_INPUT, FORMAT_PREFIX, FORMAT_NEWLINE, FORMAT_INPUT.uppercase(), FORMAT_PREFIX.uppercase(), FORMAT_NEWLINE.uppercase()),
                    arrayOf(str, if (su) suPrefixText else prefixText, Tuils.NEWLINE, str, if (su) suPrefixText else prefixText, Tuils.NEWLINE),
                )
                s
            }

            CATEGORY_OUTPUT -> {
                val str = t.toString()
                val so = Tuils.span(outputFormat, outputColor)
                TextUtils.replace(
                    so,
                    arrayOf(FORMAT_OUTPUT, FORMAT_NEWLINE, FORMAT_OUTPUT.uppercase(), FORMAT_NEWLINE.uppercase()),
                    arrayOf(str, Tuils.NEWLINE, str, Tuils.NEWLINE),
                )
            }

            CATEGORY_NO_COLOR -> t

            else -> null
        }
    }
}
