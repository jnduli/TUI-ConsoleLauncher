package ohi.andre.consolelauncher.managers.suggestions

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import ohi.andre.consolelauncher.UIManager.Companion.getListOfIntValues
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandParser
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.launchable.Launchable
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.LogTuils

import java.util.Locale

/**
 * Created by francescoandreuzzi on 25/12/15.
 */
class SuggestionsManager(
    private var pack: CommandContext,
    private val mTerminalAdapter: TerminalManager,
    private val aliasManager: AliasManager,
    private val appsManager: AppsManager,
    private val contacts: ContactManager?,
    parser: CommandParser,
) {
    private val generator = SuggestionGenerator(pack, aliasManager, appsManager, contacts, parser)

    private var hideViewValue: HideSuggestionViewValues? = null

    private val doubleSpaceFirstSuggestion: Boolean
    private var lastFirst: Suggestion? = null

    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    private val _visible = MutableStateFlow(true)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inputFlow =
        MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    data class SuggestionColors(val background: Int, val foreground: Int)

    private val suggBg: Map<SuggestionCategory, Int> = mapOf(
        SuggestionCategory.APP to XMLPrefsManager.getColor(Suggestions.apps_bg_color),
        SuggestionCategory.APP_GROUP to XMLPrefsManager.getColor(Suggestions.apps_bg_color),
        SuggestionCategory.ALIAS to XMLPrefsManager.getColor(Suggestions.alias_bg_color),
        SuggestionCategory.COMMAND to XMLPrefsManager.getColor(Suggestions.cmd_bg_color),
        SuggestionCategory.CONTACT to XMLPrefsManager.getColor(Suggestions.contact_bg_color),
        SuggestionCategory.FILE to XMLPrefsManager.getColor(Suggestions.file_bg_color),
        SuggestionCategory.CONFIG_FILE to XMLPrefsManager.getColor(Suggestions.file_bg_color),
    )
    private val suggDefaultBg: Int = XMLPrefsManager.getColor(Suggestions.default_bg_color)
    private val suggText: Map<SuggestionCategory, Int> = mapOf(
        SuggestionCategory.APP to XMLPrefsManager.getColor(Suggestions.apps_text_color),
        SuggestionCategory.APP_GROUP to XMLPrefsManager.getColor(Suggestions.apps_text_color),
        SuggestionCategory.ALIAS to XMLPrefsManager.getColor(Suggestions.alias_text_color),
        SuggestionCategory.COMMAND to XMLPrefsManager.getColor(Suggestions.cmd_text_color),
        SuggestionCategory.CONTACT to XMLPrefsManager.getColor(Suggestions.contact_text_color),
        SuggestionCategory.FILE to XMLPrefsManager.getColor(Suggestions.file_text_color),
        SuggestionCategory.CONFIG_FILE to XMLPrefsManager.getColor(Suggestions.file_text_color),
    )
    private val suggDefaultText: Int = XMLPrefsManager.getColor(Suggestions.default_text_color)

    /** Background/foreground for a suggestion chip - an app/app-group suggestion belonging to a
     * user-defined [AppsManager.Group] uses the group's colors instead of the type default. */
    fun colorsFor(suggestion: Suggestion): SuggestionColors {
        val group = when (val kind = suggestion.kind) {
            is SuggestionKind.App -> kind.launchable?.let { launchable ->
                appsManager.groups.firstOrNull { it.contains(launchable) }
            }
            is SuggestionKind.AppGroup -> kind.group
            else -> null
        }
        if (group != null) {
            return SuggestionColors(group.bgColor, group.foreColor)
        }

        val category = suggestion.kind.category
        return SuggestionColors(
            background = suggBg[category] ?: suggDefaultBg,
            foreground = suggText[category] ?: suggDefaultText,
        )
    }

    /** [Suggestions.suggestions_spaces]: "[margin horizontal],[margin vertical],[padding horizontal],[padding vertical]" (pixels). */
    data class ChipSpacing(val marginH: Int, val marginV: Int, val paddingH: Int, val paddingV: Int)
    val chipSpacing: ChipSpacing
    val chipTextSizeSp: Int = XMLPrefsManager.getInt(Suggestions.suggestions_size)

    fun dispose() {
        scope.cancel()
    }

    fun clear() {
        _suggestions.value = emptyList()
    }

    fun hide() {
        _visible.value = false
    }

    init {
        doubleSpaceFirstSuggestion =
            XMLPrefsManager.getBoolean(Suggestions.double_space_click_first_suggestion)

        val spaces = getListOfIntValues(XMLPrefsManager.get(Suggestions.suggestions_spaces), 4, 0)
        chipSpacing = ChipSpacing(
            marginH = spaces.getOrElse(0) { 0 },
            marginV = spaces.getOrElse(1) { 0 },
            paddingH = spaces.getOrElse(2) { 0 },
            paddingV = spaces.getOrElse(3) { 0 },
        )

        try {
            hideViewValue = HideSuggestionViewValues.valueOf(
                XMLPrefsManager.get(Suggestions.hide_suggestions_when_empty).uppercase(
                    Locale.getDefault()
                )
            )
        } catch (e: Exception) {
            hideViewValue = HideSuggestionViewValues.valueOf(
                Suggestions.hide_suggestions_when_empty.defaultValue().uppercase(
                    Locale.getDefault()
                )
            )
        }

        scope.launch {
            inputFlow
                .debounce(SUGGESTION_DEBOUNCE_MS)
                .mapLatest { input -> input to computeSuggestions(input) }
                .collect { (input, suggestions) -> applyResult(input, suggestions) }
        }
    }

    fun show() {
        _visible.value = true
    }

    fun clickSuggestion(suggestion: Suggestion) {
        val execOnClick = suggestion.exec

        val text = suggestion.resolvedText()
        val input = mTerminalAdapter.input

        if (suggestion.kind is SuggestionKind.Permanent) {
            mTerminalAdapter.input = input + text
        } else {
            val addSpace =
                suggestion.kind !is SuggestionKind.File && suggestion.kind !is SuggestionKind.Color

            // TerminalManager only cares about a Launchable payload (to fast-relaunch on enter).
            val launchable = (suggestion.kind as? SuggestionKind.App)?.launchable

            mTerminalAdapter.setInput(
                text + (if (addSpace) Tuils.SPACE else Tuils.EMPTYSTRING),
                launchable
            )
        }

        if (execOnClick) {
            mTerminalAdapter.simulateEnter()
        } else {
            mTerminalAdapter.focusInputEnd()
        }
    }

    val defaultAppSlotCount: Int get() = AppsManager.DEFAULT_APP_SLOT_COUNT

    fun defaultAppSlotLabel(index: Int): String = appsManager.defaultAppSlotLabel(index)

    /** Pins (or unpins, if [launchable] is null) an app into a no-input suggestion slot. Runs off the UI thread. */
    fun pinDefaultApp(context: Context, index: Int, launchable: Launchable?) {
        scope.launch {
            appsManager.pinDefaultApp(context, index, launchable)
        }
    }

    fun requestSuggestion(input: String) {
        try {
            val l = input.length
            if (doubleSpaceFirstSuggestion && l > 0 && input.get(l - 1) == ' ') {
                if (input.get(l - 2) == ' ') {
//                    double space
                    if (lastFirst == null) {
                        val s = _suggestions.value.firstOrNull()
                        if (s != null && !input.trim { it <= ' ' }.endsWith(s.resolvedText())) lastFirst = s
                    }

                    val lf = lastFirst
                    if (lf != null) {
                        mTerminalAdapter.input =
                            if (0 == l - 2) Tuils.EMPTYSTRING else input.substring(0, l - 2)
                        clickSuggestion(lf)
                        return
                    }
                } else {
//                    single space
                    val newFirst = _suggestions.value.firstOrNull()
                    lastFirst = newFirst
                    if (newFirst?.resolvedText() == input.trim { it <= ' ' }) {
                        lastFirst = null
                    }
                }
            } else {
                lastFirst = null
            }
        } catch (e: Exception) {
//            this will trigger an error when there's a single space in the input field, but it's not a problem
            LogTuils.log(e)
            LogTuils.toFile(e)
        }

        inputFlow.tryEmit(input)
    }

    /** Runs off the UI thread; cancelled mid-flight (via [mapLatest]) the moment a newer [inputFlow] value arrives. */
    private suspend fun computeSuggestions(input: String): List<Suggestion?>? {
        val before: String
        val lastWord: String

        val lastSpace = input.lastIndexOf(Tuils.SPACE)
        if (lastSpace == -1) {
            before = Tuils.EMPTYSTRING
            lastWord = input
        } else {
            before = input.substring(0, lastSpace)
            lastWord = input.substring(lastSpace + 1, input.length)
        }

        return try {
            generator.getSuggestions(before, lastWord, currentCoroutineContext())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogTuils.log(e)
            LogTuils.toFile(e)
            null
        }
    }

    private fun applyResult(input: String, suggestions: List<Suggestion?>?) {
        if (suggestions == null) return

        if (suggestions.isEmpty()) {
            _suggestions.value = emptyList()

            if (hideViewValue == HideSuggestionViewValues.ALWAYS || (hideViewValue == HideSuggestionViewValues.TRUE && input.isEmpty())) {
                hide()
            }

            return
        }

        show()

        _suggestions.value = suggestions.filterNotNull()
    }

    private companion object {
        const val SUGGESTION_DEBOUNCE_MS = 50L
    }
}
