package ohi.andre.consolelauncher.managers.suggestions

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import it.andreuzzi.comparestring2.AlgMap
import it.andreuzzi.comparestring2.AlgMap.Alg
import it.andreuzzi.comparestring2.CompareObjects
import it.andreuzzi.comparestring2.CompareStrings
import it.andreuzzi.comparestring2.StringableObject
import it.andreuzzi.comparestring2.algs.interfaces.Algorithm
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager.Companion.getListOfIntValues
import ohi.andre.consolelauncher.commands.Command
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandTuils
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.commands.main.specific.PermanentSuggestionCommand
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.ContactManager.Contact
import ohi.andre.consolelauncher.managers.FileManager
import ohi.andre.consolelauncher.managers.Launchable
import ohi.andre.consolelauncher.managers.RssManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.music.Song
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.notifications.reply.BoundApp
import ohi.andre.consolelauncher.managers.notifications.reply.ReplyManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager.XMLPrefsRoot
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Reply
import ohi.andre.consolelauncher.managers.xml.options.Rss
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern
import kotlin.collections.ArrayList
import kotlin.collections.MutableIterator
import kotlin.collections.MutableList
import kotlin.collections.dropLastWhile
import kotlin.collections.indices
import kotlin.collections.toTypedArray
import kotlin.math.abs
import kotlin.math.max

/**
 * Created by francescoandreuzzi on 25/12/15.
 */
class SuggestionsManager(
    private val suggestionsView: LinearLayout,
    private var pack: MainPack,
    private val mTerminalAdapter: TerminalManager
) {
    private var hideViewValue: HideSuggestionViewValues? = null

    private val FIRST_INTERVAL = 6

    private val SPLITTERS = arrayOf<String?>(Tuils.SPACE)
    private val FILE_SPLITTERS = arrayOf<String?>(Tuils.SPACE, "-", "_")
    private val XML_PREFS_SPLITTERS = arrayOf<String?>("_")

    private val showAliasDefault: Boolean
    private val clickToLaunch: Boolean
    private val showAppsGpDefault: Boolean
    private var enabled: Boolean
    private val minCmdPriority: Int

    private val multipleCmdSeparator: String

    private val doubleSpaceFirstSuggestion: Boolean
    private var suggestionRunnable: SuggestionRunnable? = null
    private var suggestionViewParams: LinearLayout.LayoutParams? = null
    private var lastFirst: Suggestion? = null

    private val clickListener = View.OnClickListener { v: View? ->
        val suggestion = v?.getTag(R.id.suggestion_id) as? Suggestion ?: return@OnClickListener
        clickSuggestion(suggestion)
    }

    private var lastSuggestionThread: StoppableThread? = null
    private val handler = Handler()

    private val removeAllSuggestions: RemoverRunnable

    private val spaces: IntArray

    var counts: IntArray?
    var noInputCounts: IntArray?

    private val rmQuotes: Pattern = Pattern.compile("['\"]")

    var suggestionsPerCategory: Int
    var suggestionsDeadline: Float

    private val comparator: CustomComparator

    private var algInstance: Algorithm? = null
    private var alg: Alg? = null

    private val quickCompare: Int

    private fun setAlgorithm(id: Int) {
        when (id) {
            0 -> alg = AlgMap.DistAlg.LCS
            1 -> alg = AlgMap.DistAlg.OSA
            2 -> alg = AlgMap.DistAlg.QGRAM
            4 -> alg = AlgMap.NormDistAlg.COSINE
            5 -> alg = AlgMap.NormDistAlg.JACCARD
            6 -> alg = AlgMap.NormDistAlg.JAROWRINKLER
            7 -> alg = AlgMap.NormDistAlg.METRICLCS
            8 -> alg = AlgMap.NormDistAlg.NGRAM
            9 -> alg = AlgMap.NormDistAlg.NLEVENSHTEIN
            10 -> alg = AlgMap.NormDistAlg.SORENSENDICE
            11 -> alg = AlgMap.NormSimAlg.COSINE
            12 -> alg = AlgMap.NormSimAlg.JACCARD
            13 -> alg = AlgMap.NormSimAlg.JAROWRINKLER
            14 -> alg = AlgMap.NormSimAlg.NLEVENSHTEIN
            15 -> alg = AlgMap.NormSimAlg.SORENSENDICE
            16 -> alg = AlgMap.MetricDistAlg.DAMERAU
            17 -> alg = AlgMap.MetricDistAlg.JACCARD
            18 -> alg = AlgMap.MetricDistAlg.LEVENSHTEIN
            19 -> alg = AlgMap.MetricDistAlg.METRICLCS
        }

        algInstance = alg?.buildAlg(id)
    }

    fun getSuggestionView(context: Context?): TextView {
        val textView = TextView(context)
        textView.setOnClickListener(clickListener)

        textView.setFocusable(false)
        textView.setLongClickable(false)
        textView.setClickable(true)

        textView.setTypeface(Tuils.getTypeface(context))
        textView.setTextSize(XMLPrefsManager.getInt(Suggestions.suggestions_size).toFloat())

        textView.setPadding(spaces[2], spaces[3], spaces[2], spaces[3])

        textView.setLines(1)
        textView.setMaxLines(1)

        return textView
    }

    private fun stop() {
        handler.removeCallbacksAndMessages(null)
        lastSuggestionThread?.interrupt()
    }

    fun dispose() {
        stop()
    }

    fun clear() {
        stop()
        suggestionsView.removeAllViews()
    }

    var hideRunnable: Runnable = object : Runnable {
        override fun run() {
            suggestionsView.setVisibility(View.GONE)

            stop()
        }
    }

    fun hide() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            hideRunnable.run()
        } else {
            (mTerminalAdapter.mContext as Activity).runOnUiThread(hideRunnable)
        }
    }

    var showRunnable: Runnable = object : Runnable {
        override fun run() {
            suggestionsView.setVisibility(View.VISIBLE)
        }
    }

    init {
        setAlgorithm(XMLPrefsManager.getInt(Suggestions.suggestions_algorithm))

        quickCompare = XMLPrefsManager.getInt(Suggestions.suggestions_quickcompare_n)

        this.suggestionsPerCategory = XMLPrefsManager.getInt(Suggestions.suggestions_per_category)
        this.suggestionsDeadline = XMLPrefsManager.get(Suggestions.suggestions_deadline).toFloat()

        this.removeAllSuggestions = RemoverRunnable(suggestionsView)

        doubleSpaceFirstSuggestion =
            XMLPrefsManager.getBoolean(Suggestions.double_space_click_first_suggestion)
        Suggestion.Companion.appendQuotesBeforeFile =
            XMLPrefsManager.getBoolean(Behavior.append_quote_before_file)
        multipleCmdSeparator = XMLPrefsManager.get(Behavior.multiple_cmd_separator)

        enabled = true

        showAliasDefault = XMLPrefsManager.getBoolean(Suggestions.suggest_alias_default)
        showAppsGpDefault = XMLPrefsManager.getBoolean(Suggestions.suggest_appgp_default)
        clickToLaunch = XMLPrefsManager.getBoolean(Suggestions.click_to_launch)

        minCmdPriority = XMLPrefsManager.getInt(Suggestions.noinput_min_command_priority)

        spaces = getListOfIntValues(XMLPrefsManager.get(Suggestions.suggestions_spaces), 4, 0)

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

        var s = XMLPrefsManager.get(Suggestions.suggestions_order)
        var orderPattern = Pattern.compile("(\\d+)\\((\\d+)\\)")
        var m = orderPattern.matcher(s)

        var indexes: IntArray? = IntArray(4)
        counts = IntArray(4)

        var index = 0
        while (m.find() && index < indexes!!.size) {
            val type = m.group(1).toInt()

            if (type >= indexes.size) {
                Tuils.sendOutput(Color.RED, pack.context, "Invalid suggestion type: " + type)

                indexes = null
                counts = null

                break
            }

            val count = m.group(2).toInt()

            indexes[type] = index
            counts!![type] = count

            index++
        }

        s = XMLPrefsManager.get(Suggestions.noinput_suggestions_order)
        orderPattern = Pattern.compile("(\\d+)\\((\\d+)\\)")
        m = orderPattern.matcher(s)

        var noInputIndexes: IntArray? = IntArray(4)
        noInputCounts = IntArray(4)

        index = 0
        while (m.find() && index < noInputIndexes!!.size) {
            val type = m.group(1).toInt()

            if (type >= noInputIndexes.size) {
                Tuils.sendOutput(Color.RED, pack.context, "Invalid suggestion type: " + type)

                noInputIndexes = null
                noInputCounts = null

                break
            }

            val count = m.group(2).toInt()

            noInputIndexes[type] = index
            noInputCounts!![type] = count

            index++
        }

        comparator = CustomComparator(noInputIndexes!!, indexes!!)

        val uselessView = getSuggestionView(pack.context)
        uselessView.setVisibility(View.INVISIBLE)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(spaces[0], spaces[1], spaces[2], spaces[3])

        (suggestionsView.getParent() as LinearLayout).addView(uselessView, params)
    }

    fun show() {
        if (Looper.getMainLooper() == Looper.myLooper()) {
            showRunnable.run()
        } else {
            (mTerminalAdapter.mContext as Activity).runOnUiThread(showRunnable)
        }
    }

    fun enable() {
        enabled = true

        show()
    }

    fun disable() {
        enabled = false

        hide()
    }

    fun clickSuggestion(suggestion: Suggestion) {
        val execOnClick = suggestion.exec

        val text = suggestion.getText()
        val input = mTerminalAdapter.getInput()

        if (suggestion.type == Suggestion.Companion.TYPE_PERMANENT) {
            mTerminalAdapter.setInput(input + text)
        } else {
            val addSpace =
                suggestion.type != Suggestion.Companion.TYPE_FILE && suggestion.type != Suggestion.Companion.TYPE_COLOR

            if (multipleCmdSeparator.length > 0) {
//                try to understand if the user is using a multiple cmd
                val split =
                    input.split(multipleCmdSeparator.toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()

                //                not using it
                if (split.size == 1) mTerminalAdapter.setInput(
                    text + (if (addSpace) Tuils.SPACE else Tuils.EMPTYSTRING),
                    suggestion.`object`
                )
                else {
                    split[split.size - 1] = Tuils.EMPTYSTRING

                    var beforeInputs = Tuils.EMPTYSTRING
                    for (count in 0..<split.size - 1) {
                        beforeInputs = beforeInputs + split[count] + multipleCmdSeparator
                    }

                    mTerminalAdapter.setInput(
                        beforeInputs + text + (if (addSpace) Tuils.SPACE else Tuils.EMPTYSTRING),
                        suggestion.`object`
                    )
                }
            } else {
                mTerminalAdapter.setInput(
                    text + (if (addSpace) Tuils.SPACE else Tuils.EMPTYSTRING),
                    suggestion.`object`
                )
            }
        }

        if (execOnClick) {
            mTerminalAdapter.simulateEnter()
        } else {
            mTerminalAdapter.focusInputEnd()
        }
    }

    fun requestSuggestion(input: String) {
        if (!enabled) return

        val params = suggestionViewParams ?: run {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).also {
                it.setMargins(15, 0, 15, 0)
                it.gravity = Gravity.CENTER_VERTICAL
                suggestionViewParams = it
            }
        }

        if (suggestionRunnable == null) {
            val scrollView = suggestionsView.parent?.parent as? HorizontalScrollView ?: return
            suggestionRunnable = SuggestionRunnable(pack, suggestionsView, params, scrollView, spaces)
        }

        lastSuggestionThread?.interrupt()
        suggestionRunnable?.let {
            it.interrupt()
            handler.removeCallbacks(it)
        }

        try {
            val l = input.length
            if (doubleSpaceFirstSuggestion && l > 0 && input.get(l - 1) == ' ') {
                if (input.get(l - 2) == ' ') {
//                    double space
                    if (lastFirst == null && suggestionsView.getChildCount() > 0) {
                        val s =
                            suggestionsView.getChildAt(0).getTag(R.id.suggestion_id) as Suggestion
                        if (!input.trim { it <= ' ' }.endsWith(s.getText().orEmpty())) lastFirst = s
                    }

                    val lf = lastFirst
                    if (lf != null) {
                        mTerminalAdapter.setInput(
                            if (0 == l - 2) Tuils.EMPTYSTRING else input.substring(0, l - 2)
                        )
                        clickSuggestion(lf)
                        return
                    }
                } else if (suggestionsView.getChildCount() > 0) {
//                    single space
                    val newFirst =
                        suggestionsView.getChildAt(0).getTag(R.id.suggestion_id) as? Suggestion
                    lastFirst = newFirst
                    if (newFirst?.getText() == input.trim { it <= ' ' }) {
                        lastFirst = null
                    }
                }
            } else {
                lastFirst = null
            }
        } catch (e: Exception) {
//            this will trigger an error when there's a single space in the input field, but it's not a problem
            Tuils.log(e)
            Tuils.toFile(e)
        }

        lastSuggestionThread = object : StoppableThread() {
            override fun run() {
                super.run()

                val runnable = suggestionRunnable ?: return

                val before: String?
                val lastWord: String?
                val lastInput: String?
                if (multipleCmdSeparator.length > 0) {
                    val split =
                        input.split(multipleCmdSeparator.toRegex()).dropLastWhile { it.isEmpty() }
                            .toTypedArray()
                    if (split.size == 0) lastInput = input
                    else lastInput = split[split.size - 1]
                } else {
                    lastInput = input
                }

                val lastSpace = lastInput.lastIndexOf(Tuils.SPACE)
                if (lastSpace == -1) {
                    before = Tuils.EMPTYSTRING
                    lastWord = lastInput
                } else {
                    before = lastInput.substring(0, lastSpace)
                    lastWord = lastInput.substring(lastSpace + 1, lastInput.length)
                }

                val suggestions: MutableList<Suggestion?>
                try {
                    suggestions = getSuggestions(before, lastWord)
                } catch (e: Exception) {
                    Tuils.log(e)
                    Tuils.toFile(e)
                    return
                }

                if (suggestions.size == 0) {
                    (pack.context as Activity).runOnUiThread(removeAllSuggestions)
                    removeAllSuggestions.isGoingToRun = true

                    if (hideViewValue == HideSuggestionViewValues.ALWAYS || (hideViewValue == HideSuggestionViewValues.TRUE && input.length == 0)) {
                        hide()
                    }

                    return
                } else {
                    if (removeAllSuggestions.isGoingToRun) {
                        removeAllSuggestions.stop = true
                    }

                    show()
                }

                if (interrupted()) {
                    runnable.interrupt()
                    return
                }

                val existingViews = arrayOfNulls<TextView>(suggestionsView.getChildCount())
                for (count in existingViews.indices) {
                    existingViews[count] = suggestionsView.getChildAt(count) as TextView?
                }

                if (interrupted()) {
                    runnable.interrupt()
                    return
                }

                val n = suggestions.size - existingViews.size
                var toAdd: Array<TextView?>? = null
                var toRecycle: Array<TextView?>? = null
                if (n == 0) {
                    toRecycle = existingViews
                    toAdd = null
                } else if (n > 0) {
                    toRecycle = existingViews
                    toAdd = arrayOfNulls<TextView>(n)
                    for (count in toAdd.indices) {
                        toAdd[count] = getSuggestionView(pack.context)
                    }
                } else if (n < 0) {
                    toAdd = null
                    toRecycle = arrayOfNulls<TextView>(suggestions.size)
                    System.arraycopy(existingViews, 0, toRecycle, 0, toRecycle.size)
                }

                if (interrupted()) {
                    runnable.interrupt()
                    return
                }

                runnable.setN(n)
                runnable.setSuggestions(suggestions as MutableList<Suggestion>)
                runnable.setToAdd(toAdd ?: emptyArray())
                runnable.setToRecycle(toRecycle ?: emptyArray())
                runnable.reset()
                (pack.context as Activity).runOnUiThread(runnable)
            }
        }

        try {
            lastSuggestionThread?.start()
        } catch (e: InternalError) {
            Tuils.log(e)
            // Tuils.toFile(e);
        }
    }

    //    there's always a space between beforelastspace and lastword
    fun getSuggestions(beforeLastSpace: String, lastWord: String): MutableList<Suggestion?> {
        var beforeLastSpace = beforeLastSpace
        var lastWord = lastWord
        val suggestionList: MutableList<Suggestion?> = ArrayList<Suggestion?>()

        beforeLastSpace = beforeLastSpace.trim { it <= ' ' }
        lastWord = lastWord.trim { it <= ' ' }

        //        lastword = 0
        if (lastWord.length == 0) {
            //            lastword = 0 && beforeLastSpace = 0

            if (beforeLastSpace.length == 0) {
                comparator.noInput = true

                val apps = pack.appsManager.suggestedApps
                if (apps != null) {
                    var count = 0
                    while (count < apps.size && count < (noInputCounts?.get(Suggestion.Companion.TYPE_APP) ?: 0)) {
                        val app = apps[count]
                        if (app == null) {
                            count++
                            continue
                        }

                        suggestionList.add(
                            Suggestion(
                                beforeLastSpace,
                                app.publicLabel,
                                clickToLaunch,
                                Suggestion.Companion.TYPE_APP,
                                app
                            )
                        )
                        count++
                    }
                }

                suggestCommand(pack, suggestionList, null)

                if (showAliasDefault) suggestAlias(pack.aliasManager, suggestionList, lastWord)
                if (showAppsGpDefault) suggestAppGroup(
                    pack,
                    suggestionList,
                    lastWord,
                    beforeLastSpace
                )
            } else {
                comparator.noInput = false

                //                check if this is a command
                var cmd: Command? = null
                try {
                    cmd = CommandTuils.parse(beforeLastSpace, pack)
                } catch (e: Exception) {
                }

                if (cmd != null) {
                    if (cmd.cmd is PermanentSuggestionCommand) {
                        suggestPermanentSuggestions(
                            suggestionList,
                            cmd.cmd as PermanentSuggestionCommand
                        )
                    }

                    if (cmd.mArgs != null && cmd.mArgs.size > 0 && cmd.cmd is ParamCommand && cmd.nArgs >= 1 && cmd.mArgs[0] is Param && (cmd.mArgs[0] as Param).args().size + 1 == cmd.nArgs) {
//                        nothing
                    } else {
                        if (cmd.cmd is ParamCommand && (cmd.mArgs == null || cmd.mArgs.size == 0 || cmd.mArgs[0] is String)) suggestParams(
                            pack,
                            suggestionList,
                            cmd.cmd as ParamCommand,
                            beforeLastSpace,
                            null
                        )
                        else suggestArgs(pack, cmd.nextArg(), suggestionList, beforeLastSpace)
                    }
                } else {
                    val split = rmQuotes.matcher(beforeLastSpace).replaceAll(Tuils.EMPTYSTRING)
                        .split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    var isShellCmd = false
                    for (s in split) {
                        if (needsFileSuggestion(s)) {
                            isShellCmd = true
                            break
                        }
                    }

                    if (isShellCmd) {
                        suggestFile(pack, suggestionList, Tuils.EMPTYSTRING, beforeLastSpace)
                    } else {
//                        ==> app
                        if (!suggestAppInsideGroup(
                                pack,
                                suggestionList,
                                Tuils.EMPTYSTRING,
                                beforeLastSpace,
                                false
                            )
                        ) suggestApp(
                            pack,
                            suggestionList,
                            beforeLastSpace + Tuils.SPACE,
                            Tuils.EMPTYSTRING
                        )
                    }
                }
            }
        } else {
            comparator.noInput = false

            if (beforeLastSpace.length > 0) {
//                lastword > 0 && beforeLastSpace  > 0
                var cmd: Command? = null
                try {
                    cmd = CommandTuils.parse(beforeLastSpace, pack)
                } catch (e: Exception) {
                }

                if (cmd != null) {
                    if (cmd.cmd is PermanentSuggestionCommand) {
                        suggestPermanentSuggestions(
                            suggestionList,
                            cmd.cmd as PermanentSuggestionCommand
                        )
                    }

                    //                    if (cmd.cmd.maxArgs() == 1 && beforeLastSpace .contains(Tuils.SPACE)) {
//                        int index = cmd.cmd.getClass().getSimpleName().length() + 1;
//
//                        lastWord = beforeLastSpace .substring(index) + lastWord;
//                    }
                    if (cmd.cmd is ParamCommand && (cmd.mArgs == null || cmd.mArgs.size == 0 || cmd.mArgs[0] is String)) {
                        suggestParams(
                            pack,
                            suggestionList,
                            cmd.cmd as ParamCommand,
                            beforeLastSpace,
                            lastWord
                        )
                    } else suggestArgs(
                        pack,
                        cmd.nextArg(),
                        suggestionList,
                        lastWord,
                        beforeLastSpace
                    )
                } else {
                    val split = beforeLastSpace.replace("['\"]".toRegex(), Tuils.EMPTYSTRING)
                        .split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    var isShellCmd = false
                    for (s in split) {
                        if (needsFileSuggestion(s)) {
                            isShellCmd = true
                            break
                        }
                    }

                    if (isShellCmd) {
                        suggestFile(pack, suggestionList, lastWord, beforeLastSpace)
                    } else {
                        if (!suggestAppInsideGroup(
                                pack,
                                suggestionList,
                                lastWord,
                                beforeLastSpace,
                                false
                            )
                        ) suggestApp(
                            pack,
                            suggestionList,
                            beforeLastSpace + Tuils.SPACE + lastWord,
                            Tuils.EMPTYSTRING
                        )
                    }
                }

                //                lastword > 0 && beforeLastSpace  = 0
            } else {
                suggestCommand(pack, suggestionList, lastWord, beforeLastSpace)
                suggestAlias(pack.aliasManager, suggestionList, lastWord)
                suggestApp(pack, suggestionList, lastWord, Tuils.EMPTYSTRING)
                suggestAppGroup(pack, suggestionList, lastWord, beforeLastSpace)
            }
        }

        Collections.sort<Suggestion?>(suggestionList, comparator)
        return suggestionList
    }

    private fun needsFileSuggestion(cmd: String): Boolean {
        return cmd.equals("ls", ignoreCase = true) || cmd.equals(
            "cd",
            ignoreCase = true
        ) || cmd.equals("mv", ignoreCase = true) || cmd.equals(
            "cp",
            ignoreCase = true
        ) || cmd.equals("rm", ignoreCase = true) || cmd.equals("cat", ignoreCase = true)
    }

    private fun suggestPermanentSuggestions(
        suggestions: MutableList<Suggestion?>,
        cmd: PermanentSuggestionCommand
    ) {
        for (s in cmd.permanentSuggestions()) {
            val sugg = Suggestion(null, s, false, Suggestion.Companion.TYPE_PERMANENT)
            suggestions.add(sugg)
        }
    }

    private fun suggestAlias(
        aliasManager: AliasManager,
        suggestions: MutableList<Suggestion?>,
        lastWord: String?
    ) {
        var canInsert =
            if (lastWord.isNullOrEmpty()) noInputCounts?.get(Suggestion.Companion.TYPE_ALIAS) ?: 0
            else counts?.get(Suggestion.Companion.TYPE_ALIAS) ?: 0

        for (a in aliasManager.getAliases(true)) {
            if (lastWord.isNullOrEmpty() || a.name.startsWith(lastWord)) {
                if (canInsert == 0) return
                canInsert--

                suggestions.add(
                    Suggestion(
                        Tuils.EMPTYSTRING,
                        a.name,
                        clickToLaunch && !a.isParametrized,
                        Suggestion.Companion.TYPE_ALIAS
                    )
                )
            }
        }
    }

    private fun suggestParams(
        pack: MainPack?,
        suggestions: MutableList<Suggestion?>,
        cmd: ParamCommand,
        beforeLastSpace: String?,
        lastWord: String?
    ) {
        val params = cmd.params()
        if (params == null) {
            return
        }

        if (lastWord == null || lastWord.length == 0) {
            for (s in cmd.params()) {
                val p = cmd.getParam(pack, s).value
                if (p == null) continue

                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s,
                        p.args().size == 0 && clickToLaunch,
                        0
                    )
                )
            }
        } else {
            for (s in cmd.params()) {
                val p = cmd.getParam(pack, s).value
                if (p == null) continue

                if (s.startsWith(lastWord) || s.replace("-", Tuils.EMPTYSTRING)
                        .startsWith(lastWord)
                ) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            s,
                            p.args().size == 0 && clickToLaunch,
                            0
                        )
                    )
                }
            }
        }
    }

    private fun suggestArgs(
        info: MainPack,
        type: Int,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String
    ) {
        when (type) {
            CommandAbstraction.FILE -> suggestFile(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.VISIBLE_PACKAGE -> suggestApp(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.COMMAND -> suggestCommand(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.CONTACTNUMBER -> suggestContact(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.SONG -> suggestSong(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.BOOLEAN -> suggestBoolean(suggestions, beforeLastSpace)
            CommandAbstraction.HIDDEN_PACKAGE -> suggestHiddenApp(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.COLOR -> suggestColor(suggestions, afterLastSpace, beforeLastSpace)
            CommandAbstraction.CONFIG_ENTRY -> suggestConfigEntry(
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.CONFIG_FILE -> suggestConfigFile(
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.DEFAULT_APP -> suggestDefaultApp(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.ALL_PACKAGES -> suggestAllPackages(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.APP_GROUP -> suggestAppGroup(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.APP_INSIDE_GROUP -> suggestAppInsideGroup(
                info,
                suggestions,
                afterLastSpace,
                beforeLastSpace,
                true
            )

            CommandAbstraction.BOUND_REPLY_APP -> suggestBoundReplyApp(
                suggestions,
                afterLastSpace,
                beforeLastSpace
            )

            CommandAbstraction.DATASTORE_PATH_TYPE -> suggestDataStoreType(
                suggestions,
                beforeLastSpace
            )
        }
    }

    private fun suggestArgs(
        info: MainPack,
        type: Int,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String
    ) {
        suggestArgs(info, type, suggestions, null, beforeLastSpace)
    }

    private fun suggestBoolean(suggestions: MutableList<Suggestion?>, beforeLastSpace: String?) {
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "true",
                clickToLaunch,
                Suggestion.Companion.TYPE_BOOLEAN
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "false",
                clickToLaunch,
                Suggestion.Companion.TYPE_BOOLEAN
            )
        )
    }

    private fun suggestFile(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        var afterLastSpace = afterLastSpace
        val noAfterLastSpace = afterLastSpace == null || afterLastSpace.length == 0
        val afterLastSpaceNotEndsWithSeparator =
            noAfterLastSpace || !afterLastSpace.endsWith(File.separator)

        if (noAfterLastSpace || afterLastSpaceNotEndsWithSeparator) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    File.separator,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpace
                )
            )
        }

        if (Suggestion.Companion.appendQuotesBeforeFile && !noAfterLastSpace && !afterLastSpace.endsWith(
                SINGLE_QUOTE
            ) && !afterLastSpace.endsWith(DOUBLE_QUOTES)
        ) suggestions.add(
            Suggestion(
                beforeLastSpace,
                SINGLE_QUOTE,
                false,
                Suggestion.Companion.TYPE_FILE,
                afterLastSpace
            )
        )

        if (noAfterLastSpace) {
            suggestFilesInDir(null, suggestions, info.currentDirectory, beforeLastSpace)
            return
        }

        if (!afterLastSpace.contains(File.separator)) {
            suggestFilesInDir(
                suggestions,
                info.currentDirectory,
                afterLastSpace,
                beforeLastSpace,
                null
            )
        } else {
            //            if it's ../../

            if (!afterLastSpaceNotEndsWithSeparator) {
                val total = beforeLastSpace + Tuils.SPACE + afterLastSpace
                val quotesCount: Int =
                    total.length - total.replace(DOUBLE_QUOTES, Tuils.EMPTYSTRING).replace(
                        SINGLE_QUOTE, Tuils.EMPTYSTRING
                    ).length

                if (quotesCount > 0) {
                    val singleQIndex: Int = total.lastIndexOf(SINGLE_QUOTE)
                    val doubleQIndex: Int = total.lastIndexOf(DOUBLE_QUOTES)

                    val lastQuote = max(singleQIndex, doubleQIndex)

                    val file = total.substring(lastQuote + abs(quotesCount % 2 - 2))
                    val dirInfo = FileManager.cd(info.currentDirectory, file)
                    suggestFilesInDir(afterLastSpace, suggestions, dirInfo.file, beforeLastSpace)
                } else {
//                    removes the /
                    afterLastSpace = afterLastSpace.substring(0, afterLastSpace.length - 1)
                    val dirInfo = FileManager.cd(info.currentDirectory, afterLastSpace)
                    suggestFilesInDir(
                        afterLastSpace + File.separator,
                        suggestions,
                        dirInfo.file,
                        beforeLastSpace
                    )
                }
            } else {
                val originalAfterLastSpace: String = afterLastSpace
                afterLastSpace = rmQuotes.matcher(afterLastSpace).replaceAll(Tuils.EMPTYSTRING)

                val index = afterLastSpace.lastIndexOf(File.separator)
                val dirInfo =
                    FileManager.cd(info.currentDirectory, afterLastSpace.substring(0, index))

                val originalIndex = originalAfterLastSpace.lastIndexOf(File.separator)

                val alsals = originalAfterLastSpace.substring(0, originalIndex + 1)
                val als = originalAfterLastSpace.substring(originalIndex + 1)

                //                beforeLastSpace  = beforeLastSpace + Tuils.SPACE + hold;
                suggestFilesInDir(suggestions, dirInfo.file, als, beforeLastSpace, alsals)
            }
        }
    }

    private fun suggestFilesInDir(
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        afterLastSeparator: String?,
        beforeLastSpace: String?,
        afterLastSpaceWithoutALS: String?
    ) {
        if (dir == null || !dir.isDirectory()) return

        if (afterLastSeparator == null || afterLastSeparator.length == 0) {
            suggestFilesInDir(null, suggestions, dir, beforeLastSpace)
            return
        }

        val files = dir.list()
        if (files == null) {
            return
        }

        //        Tuils.log("bls", beforeLastSpace);
//        Tuils.log("als", afterLastSeparator);
//        Tuils.log("alsals", afterLastSpaceWithoutALS);
        val temp = rmQuotes.matcher(afterLastSeparator).replaceAll(Tuils.EMPTYSTRING)

        val counter = quickCompare(
            temp,
            files,
            suggestions,
            beforeLastSpace,
            suggestionsPerCategory,
            false,
            Suggestion.Companion.TYPE_FILE,
            false
        )
        if (suggestionsPerCategory - counter <= 0) return

        val fs = CompareStrings.topMatchesWithDeadline(
            temp,
            files,
            suggestionsPerCategory - counter,
            suggestionsDeadline,
            FILE_SPLITTERS,
            algInstance,
            alg
        )
        for (f in fs) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    f,
                    false,
                    Suggestion.Companion.TYPE_FILE,
                    afterLastSpaceWithoutALS
                )
            )
        }
    }

    private fun quickCompare(
        s1: String,
        ss: Array<String>,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        max: Int,
        exec: Boolean,
        type: Int,
        tag: Any?
    ): Int {
        if (s1.length > quickCompare) return 0

        var counter = 0

        for (c in ss.indices) {
            if (counter >= max) break

            if (s1.length <= quickCompare && ss[c].lowercase(Locale.getDefault()).startsWith(s1)) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        ss[c],
                        exec,
                        type,
                        if (tag is Boolean) (if (tag) ss[c] else null) else tag
                    )
                )

                ss[c] = Tuils.EMPTYSTRING

                counter++
            }
        }

        return counter
    }

    private fun quickCompare(
        s1: String,
        ss: MutableList<out StringableObject>,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        max: Int,
        exec: Boolean,
        type: Int,
        tag: Any?
    ): Int {
        if (s1.length > quickCompare) return 0

        var counter = 0

        val it: MutableIterator<out StringableObject> = ss.iterator()

        while (it.hasNext()) {
            if (counter >= max) break

            val o = it.next()

            if (s1.length <= quickCompare && o.getLowercaseString().startsWith(s1)) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        o.getString(),
                        exec,
                        type,
                        if (tag is Boolean) (if (tag) o else null) else tag
                    )
                )

                it.remove()

                counter++
            }
        }

        return counter
    }

    private fun suggestFilesInDir(
        afterLastSpaceHolder: String?,
        suggestions: MutableList<Suggestion?>,
        dir: File?,
        beforeLastSpace: String?
    ) {
        if (dir == null || !dir.isDirectory()) {
            return
        }

        try {
            val files = dir.list()
            if (files == null) {
                return
            }
            Arrays.sort(files)
            for (s in files) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s,
                        false,
                        Suggestion.Companion.TYPE_FILE,
                        afterLastSpaceHolder
                    )
                )
            }
        } catch (e: NullPointerException) {
            Tuils.log(e)
        }
    }

    private fun suggestContact(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val contacts = info.contacts.getContacts()
        if (contacts == null || contacts.size == 0) return

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (contact in contacts) suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    contact.name,
                    true,
                    Suggestion.Companion.TYPE_CONTACT,
                    contact
                )
            )
        } else {
            val counter = quickCompare(
                afterLastSpace,
                contacts,
                suggestions,
                beforeLastSpace,
                suggestionsPerCategory,
                true,
                Suggestion.Companion.TYPE_CONTACT,
                true
            )
            if (suggestionsPerCategory - counter <= 0) return

            val cts = CompareObjects.topMatchesWithDeadline<Contact?>(
                Contact::class.java,
                afterLastSpace,
                contacts.size,
                contacts,
                suggestionsPerCategory - counter,
                suggestionsDeadline,
                SPLITTERS,
                algInstance,
                alg
            )
            for (c in cts) {
                if (c == null) break
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        c.name,
                        clickToLaunch,
                        Suggestion.Companion.TYPE_CONTACT,
                        c
                    )
                )
            }
        }
    }

    private fun suggestDataStoreType(
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?
    ) {
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "json",
                false,
                Suggestion.Companion.TYPE_BOOLEAN
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "xpath",
                false,
                Suggestion.Companion.TYPE_BOOLEAN
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "format",
                false,
                Suggestion.Companion.TYPE_BOOLEAN
            )
        )
    }

    private fun suggestSong(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (info.player == null) return

        val songs = info.player.getSongs()
        if (songs == null || songs.size == 0) return

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (s in songs) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s.getTitle(),
                        clickToLaunch,
                        Suggestion.Companion.TYPE_SONG
                    )
                )
            }
        } else {
            val counter = quickCompare(
                afterLastSpace,
                songs,
                suggestions,
                beforeLastSpace,
                suggestionsPerCategory,
                clickToLaunch,
                Suggestion.Companion.TYPE_SONG,
                false
            )
            if (suggestionsPerCategory - counter <= 0) return

            val ss = CompareObjects.topMatchesWithDeadline<Song?>(
                Song::class.java,
                afterLastSpace,
                songs.size,
                songs,
                suggestionsPerCategory - counter,
                suggestionsDeadline,
                SPLITTERS,
                algInstance,
                alg
            )
            for (s in ss) {
                if (s == null) break
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s.getTitle(),
                        clickToLaunch,
                        Suggestion.Companion.TYPE_SONG
                    )
                )
            }
        }
    }

    private fun suggestCommand(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        var afterLastSpace = afterLastSpace
        if (afterLastSpace == null || afterLastSpace.length == 0) {
            suggestCommand(info, suggestions, beforeLastSpace)
            return
        }

        if (afterLastSpace.length <= FIRST_INTERVAL) {
            afterLastSpace = afterLastSpace.lowercase(Locale.getDefault()).trim { it <= ' ' }

            val cmds = info.commandGroup.getCommandNames()
            if (cmds == null) return

            var canInsert = counts?.get(Suggestion.Companion.TYPE_COMMAND) ?: 0
            for (s in cmds) {
                if (canInsert == 0 || Thread.currentThread().isInterrupted()) return

                if (s.startsWith(afterLastSpace)) {
                    val cmd = info.commandGroup.getCommandByName(s)
                    val args = cmd.argType()
                    val exec = args == null || args.size == 0
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            s,
                            exec && clickToLaunch,
                            Suggestion.Companion.TYPE_COMMAND
                        )
                    )
                    canInsert--
                }
            }
        }
    }

    private fun suggestCommand(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?
    ) {
        val cmds = info.commandGroup.getCommands()
        if (cmds == null) return

        //        if there's a beforelastspace -> help ...
        var canInsert =
            if (beforeLastSpace != null && beforeLastSpace.length > 0) Int.MAX_VALUE else noInputCounts?.get(Suggestion.Companion.TYPE_COMMAND) ?: 0

        for (cmd in cmds) {
            if (canInsert == 0 || Thread.currentThread().isInterrupted()) return

            if (info.cmdPrefs.getPriority(cmd) >= minCmdPriority) {
                val args = cmd.argType()
                val exec = args == null || args.size == 0

                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        cmd.javaClass.getSimpleName(),
                        exec && clickToLaunch,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
                canInsert--
            }
        }
    }

    private fun suggestColor(
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (afterLastSpace == null || afterLastSpace.length == 0 || (afterLastSpace.length == 1 && afterLastSpace.get(
                0
            ) != '#')
        ) {
            suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    "#",
                    false,
                    Suggestion.Companion.TYPE_COLOR
                )
            )
        }
    }

    private fun suggestApp(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        suggestApp(info.appsManager.shownApps(), suggestions, afterLastSpace, beforeLastSpace, true)
    }

    private fun suggestHiddenApp(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        suggestApp(
            info.appsManager.hiddenApps(),
            suggestions,
            afterLastSpace,
            beforeLastSpace,
            false
        )
    }

    private fun suggestAllPackages(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val apps: MutableList<Launchable> = ArrayList<Launchable>(info.appsManager.shownApps())
        apps.addAll(info.appsManager.hiddenApps())
        suggestApp(apps, suggestions, afterLastSpace, beforeLastSpace, true)
    }

    private fun suggestDefaultApp(
        info: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "most_used",
                false,
                Suggestion.Companion.TYPE_PERMANENT
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "null",
                false,
                Suggestion.Companion.TYPE_PERMANENT
            )
        )

        suggestApp(info.appsManager.shownApps(), suggestions, afterLastSpace, beforeLastSpace, true)
    }

    private fun suggestApp(
        apps: MutableList<Launchable>?,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?,
        canClickToLaunch: Boolean
    ) {
        var apps = apps
        if (apps == null || apps.size == 0) return

        apps = ArrayList<Launchable?>(apps) as MutableList<Launchable>?

        var canInsert = counts?.get(Suggestion.Companion.TYPE_APP) ?: 0
        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (l in apps!!) {
                if (canInsert == 0) return
                canInsert--

                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        l.publicLabel,
                        canClickToLaunch && clickToLaunch,
                        Suggestion.Companion.TYPE_APP,
                        l
                    )
                )
            }
        } else {
            val counter = quickCompare(
                afterLastSpace,
                apps!!,
                suggestions,
                beforeLastSpace,
                canInsert,
                canClickToLaunch && clickToLaunch,
                Suggestion.Companion.TYPE_APP,
                canClickToLaunch && clickToLaunch
            )
            if (canInsert - counter <= 0) return

            val infos = CompareObjects.topMatchesWithDeadline<Launchable?>(
                Launchable::class.java,
                afterLastSpace,
                apps!!.size,
                apps,
                canInsert - counter,
                suggestionsDeadline,
                SPLITTERS,
                algInstance,
                alg
            )
            for (i in infos) {
                if (i == null) break

                if (canInsert == 0) return
                canInsert--

                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        i.publicLabel,
                        canClickToLaunch && clickToLaunch,
                        Suggestion.Companion.TYPE_APP,
                        if (canClickToLaunch && clickToLaunch) i else null
                    )
                )
            }
        }
    }

    private fun suggestConfigEntry(
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        if (CommandTuils.xmlPrefsEntrys == null) {
            CommandTuils.xmlPrefsEntrys = ArrayList<XMLPrefsSave?>()

            for (element in XMLPrefsRoot.entries.toTypedArray()) CommandTuils.xmlPrefsEntrys.addAll(
                element.enums
            )

            Collections.addAll<Apps?>(CommandTuils.xmlPrefsEntrys, *Apps.entries.toTypedArray())
            Collections.addAll<Notifications?>(
                CommandTuils.xmlPrefsEntrys,
                *Notifications.entries.toTypedArray()
            )
            Collections.addAll<Rss?>(CommandTuils.xmlPrefsEntrys, *Rss.entries.toTypedArray())
            Collections.addAll<Reply?>(CommandTuils.xmlPrefsEntrys, *Reply.entries.toTypedArray())
        }

        val list: MutableList<XMLPrefsSave> = ArrayList<XMLPrefsSave>(CommandTuils.xmlPrefsEntrys)

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (s in list) {
                val sg =
                    Suggestion(beforeLastSpace, s.label(), false, Suggestion.Companion.TYPE_COMMAND)
                suggestions.add(sg)
            }
        } else {
            val counter = quickCompare(
                afterLastSpace,
                list,
                suggestions,
                beforeLastSpace,
                suggestionsPerCategory,
                false,
                Suggestion.Companion.TYPE_COMMAND,
                false
            )
            if (suggestionsPerCategory - counter <= 0) return

            val saves = CompareObjects.topMatchesWithDeadline<XMLPrefsSave?>(
                XMLPrefsSave::class.java,
                afterLastSpace,
                list.size,
                list,
                suggestionsPerCategory - counter,
                suggestionsDeadline,
                XML_PREFS_SPLITTERS,
                algInstance,
                alg
            )
            for (s in saves) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        s.label(),
                        false,
                        Suggestion.Companion.TYPE_COMMAND
                    )
                )
            }
        }
    }

    private fun suggestConfigFile(
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        var afterLastSpace = afterLastSpace
        if (CommandTuils.xmlPrefsFiles == null) {
            CommandTuils.xmlPrefsFiles = ArrayList<String?>()
            for (element in XMLPrefsRoot.entries.toTypedArray()) CommandTuils.xmlPrefsFiles.add(
                element.path
            )
            CommandTuils.xmlPrefsFiles.add(AppsManager.PATH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) CommandTuils.xmlPrefsFiles.add(
                ReplyManager.PATH
            )
            CommandTuils.xmlPrefsFiles.add(NotificationManager.PATH)
            CommandTuils.xmlPrefsFiles.add(RssManager.PATH)
        }

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (s in CommandTuils.xmlPrefsFiles) {
                val sg = Suggestion(
                    beforeLastSpace,
                    s,
                    false,
                    Suggestion.Companion.TYPE_CONFIGFILE,
                    afterLastSpace
                )
                suggestions.add(sg)
            }
        } else if (afterLastSpace.length <= FIRST_INTERVAL) {
            afterLastSpace = afterLastSpace.trim { it <= ' ' }.lowercase(Locale.getDefault())
            for (s in CommandTuils.xmlPrefsFiles) {
                if (Thread.currentThread().isInterrupted()) return

                if (s.startsWith(afterLastSpace)) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            s,
                            false,
                            Suggestion.Companion.TYPE_CONFIGFILE,
                            afterLastSpace
                        )
                    )
                }
            }
        }
    }

    private fun suggestAppGroup(
        pack: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val groups: MutableList<AppsManager.Group> =
            ArrayList<AppsManager.Group>(pack.appsManager.groups)
        if (groups.size == 0) return

        var canInsert: Int
        if (afterLastSpace == null || afterLastSpace.length == 0) {
            canInsert = noInputCounts?.get(Suggestion.Companion.TYPE_APPGP) ?: 0
            for (g in groups) {
                if (canInsert == 0) return
                canInsert--

                val sg = Suggestion(
                    beforeLastSpace,
                    g.name() ?: continue,
                    false,
                    Suggestion.Companion.TYPE_APPGP,
                    g
                )
                suggestions.add(sg)
            }
        } else {
            canInsert = counts?.get(Suggestion.Companion.TYPE_APPGP) ?: 0

            val counter = quickCompare(
                afterLastSpace,
                groups,
                suggestions,
                beforeLastSpace,
                canInsert,
                false,
                Suggestion.Companion.TYPE_APPGP,
                true
            )
            if (canInsert - counter <= 0) return

            val gps = CompareObjects.topMatchesWithDeadline<AppsManager.Group?>(
                AppsManager.Group::class.java,
                afterLastSpace,
                groups.size,
                groups,
                canInsert,
                suggestionsDeadline,
                SPLITTERS,
                algInstance,
                alg
            )
            for (g in gps) {
                if (g == null) break
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        g.name() ?: continue,
                        false,
                        Suggestion.Companion.TYPE_APPGP,
                        g
                    )
                )
            }
        }
    }

    private fun suggestAppInsideGroup(
        pack: MainPack,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String,
        keepGroupName: Boolean
    ): Boolean {
        var beforeLastSpace = beforeLastSpace
        var index = -1

        var app: String? = Tuils.EMPTYSTRING

        if (!beforeLastSpace.contains(Tuils.SPACE)) {
            index = Tuils.find(beforeLastSpace, pack.appsManager.groups)
            app = afterLastSpace
            if (!keepGroupName) beforeLastSpace = Tuils.EMPTYSTRING
        } else {
            val split = beforeLastSpace.split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            var count = 0
            while (count < split.size) {
                index = Tuils.find(split[count], pack.appsManager.groups)
                if (index != -1) {
                    beforeLastSpace = Tuils.EMPTYSTRING
                    var i = 0
                    while ((if (keepGroupName) i <= count else i < count)) {
                        beforeLastSpace = beforeLastSpace + split[i] + Tuils.SPACE
                        i++
                    }
                    beforeLastSpace = beforeLastSpace.trim { it <= ' ' }

                    count += 1
                    while (count < split.size) {
                        app = app + split[count] + Tuils.SPACE
                        count++
                    }
                    if (afterLastSpace != null) app = app + Tuils.SPACE + afterLastSpace
                    app = app?.trim { it <= ' ' }

                    break
                }
                count++
            }
        }

        if (index == -1) return false

        val g = pack.appsManager.groups.get(index)

        val apps: MutableList<Launchable> =
            ArrayList((g.members() as? Collection<Launchable>) ?: emptyList())
        if (apps.size > 0) {
            if (app == null || app.length == 0) {
                for (o in apps) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            o.publicLabel,
                            clickToLaunch,
                            Suggestion.Companion.TYPE_APP,
                            o
                        )
                    )
                }
            } else {
                val counter = quickCompare(
                    app,
                    apps,
                    suggestions,
                    beforeLastSpace,
                    Int.Companion.MAX_VALUE,
                    clickToLaunch,
                    Suggestion.Companion.TYPE_APP,
                    true
                )
                if (counter == apps.size) return true

                val infos = CompareObjects.topMatchesWithDeadline<Launchable?>(
                    Launchable::class.java,
                    app,
                    apps.size,
                    apps,
                    apps.size,
                    suggestionsDeadline,
                    SPLITTERS,
                    algInstance,
                    alg
                )
                for (gli in infos) {
                    if (gli == null) break
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            gli.publicLabel,
                            clickToLaunch,
                            Suggestion.Companion.TYPE_APP,
                            gli
                        )
                    )
                }
            }
        }

        return true
    }

    private fun suggestBoundReplyApp(
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ): Boolean {
        val apps: MutableList<BoundApp> = ArrayList<BoundApp>(ReplyManager.boundApps)
        if (apps.size == 0) return false

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (b in apps) {
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        b.label,
                        false,
                        Suggestion.Companion.TYPE_APP
                    )
                )
            }
        } else {
            val counter = quickCompare(
                afterLastSpace,
                apps,
                suggestions,
                beforeLastSpace,
                suggestionsPerCategory,
                false,
                Suggestion.Companion.TYPE_APP,
                false
            )
            if (suggestionsPerCategory - counter <= 0) return true

            val b = CompareObjects.topMatchesWithDeadline<BoundApp?>(
                BoundApp::class.java,
                afterLastSpace,
                apps.size,
                apps,
                suggestionsPerCategory - counter,
                suggestionsDeadline,
                SPLITTERS,
                algInstance,
                alg
            )
            for (ba in b) {
                if (ba == null) break
                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        ba.label,
                        false,
                        Suggestion.Companion.TYPE_APP
                    )
                )
            }
        }

        return true
    }

    class Suggestion {
        @JvmField
        var text: String?
        var textBefore: String?

        var exec: Boolean
        @JvmField
        var type: Int

        @JvmField
        var `object`: Any?

        constructor(beforeLastSpace: String?, text: String, exec: Boolean, type: Int) {
            this.textBefore = beforeLastSpace
            this.text = text

            this.exec = exec
            this.type = type

            this.`object` = null
        }

        constructor(beforeLastSpace: String?, text: String, exec: Boolean, type: Int, tag: Any?) {
            this.textBefore = beforeLastSpace
            this.text = text

            this.exec = exec
            this.type = type

            this.`object` = tag
        }

        fun getText(): String? {
            if (type == TYPE_CONTACT) {
                val c = `object` as Contact

                if (c.numbers.size <= c.getSelectedNumber()) c.setSelectedNumber(0)

                return textBefore + Tuils.SPACE + c.numbers.get(c.getSelectedNumber())
            } else if (type == TYPE_PERMANENT) {
                return text
            } else if (type == TYPE_FILE) {
                var lastWord = if (`object` == null) null else `object` as String
                if (lastWord == null) {
                    lastWord = Tuils.EMPTYSTRING
                }

                val textIsSpecial =
                    (text == File.separator || text == DOUBLE_QUOTES || text == SINGLE_QUOTE)
                val appendLastWord = lastWord.endsWith(File.separator) || textIsSpecial

                //                Tuils.log("-------------");
//                Tuils.log("tspe", textIsSpecial);
//                Tuils.log("tbe", textBefore.replaceAll(" ", "#"));
//                Tuils.log("lw", lastWord);
//                Tuils.log("txt", text);
                return textBefore +
                        Tuils.SPACE +
                        (if (appendLastWord) lastWord else Tuils.EMPTYSTRING) +
                        (if (appendQuotesBeforeFile && !appendLastWord) SINGLE_QUOTE else Tuils.EMPTYSTRING) +
                        text
            }

            if (textBefore.isNullOrEmpty()) {
                return text
            } else {
                return textBefore + Tuils.SPACE + text
            }
        }

        override fun toString(): String {
            return text.orEmpty()
        }

        companion object {
            //        these suggestions will appear together
            const val TYPE_APP: Int = 0
            const val TYPE_ALIAS: Int = 1
            const val TYPE_COMMAND: Int = 2
            const val TYPE_APPGP: Int = 3

            //        these suggestions will appear only in some special moments, ALONE
            const val TYPE_FILE: Int = 10
            const val TYPE_BOOLEAN: Int = 11
            const val TYPE_SONG: Int = 12
            const val TYPE_CONTACT: Int = 13
            const val TYPE_COLOR: Int = 14
            const val TYPE_PERMANENT: Int = 15
            const val TYPE_CONFIGFILE: Int = 16

            var appendQuotesBeforeFile: Boolean = false
        }
    }

    private inner class CustomComparator(var noInputIndexes: IntArray, var inputIndexes: IntArray) :
        Comparator<Suggestion?> {
        var noInput: Boolean = false

        override fun compare(o1: Suggestion?, o2: Suggestion?): Int {
            if ((o1 == null) || (o2 == null)) return 0
            if (o1.type == o2.type) return 0

            if (noInput) {
                    return noInputIndexes[o1.type] - noInputIndexes[o2.type]
            } else {
                if (o1.type < inputIndexes.size) {
                    if (o2.type < inputIndexes.size) return inputIndexes[o1.type] - inputIndexes[o2.type]
                    else return -1
                } else {
                    if (o2.type < inputIndexes.size) return 1
                    else return 0
                }
            }
        }

    }

    companion object {
        const val SINGLE_QUOTE: String = "'"
        const val DOUBLE_QUOTES: String = "\""
    }
}
