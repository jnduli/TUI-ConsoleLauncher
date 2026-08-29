package ohi.andre.consolelauncher.managers.suggestions

import android.os.Build
import it.andreuzzi.comparestring2.AlgMap
import kotlinx.coroutines.ensureActive
import it.andreuzzi.comparestring2.AlgMap.Alg
import it.andreuzzi.comparestring2.CompareObjects
import it.andreuzzi.comparestring2.CompareStrings
import it.andreuzzi.comparestring2.StringableObject
import it.andreuzzi.comparestring2.algs.interfaces.Algorithm
import ohi.andre.consolelauncher.commands.Command
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandTuils
import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.commands.main.specific.PermanentSuggestionCommand
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.ContactManager.Contact
import ohi.andre.consolelauncher.managers.FileManager
import ohi.andre.consolelauncher.managers.launchable.Launchable
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
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.tuils.LogTuils
import ohi.andre.consolelauncher.tuils.StringTuils
import ohi.andre.consolelauncher.tuils.Tuils

import java.io.File
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs
import kotlin.math.max

/**
 * Turns raw terminal input into a ranked [Suggestion] list. Stateless with respect to the UI -
 * it only reads config/managers and returns data; [SuggestionsManager] owns display/threading/clicks.
 */
class SuggestionGenerator(
    private val pack: CommandContext,
    private val aliasManager: AliasManager,
    private val appsManager: AppsManager,
    private val contacts: ContactManager?
) {
    private val FIRST_INTERVAL = 6

    /** A fuzzy-matching algorithm from the CompareString2 library, identified by the numeric [id]
     * that library's `buildAlg`/`AlgMap` API expects. Was user-configurable via the now-removed
     * Suggestions.suggestions_algorithm; only [JARO_WINKLER_SIMILARITY] is used today. */
    private enum class SimilarityAlgorithm(val id: Int, val alg: Alg) {
        LCS_DISTANCE(0, AlgMap.DistAlg.LCS),
        OSA_DISTANCE(1, AlgMap.DistAlg.OSA),
        QGRAM_DISTANCE(2, AlgMap.DistAlg.QGRAM),
        COSINE_DISTANCE(4, AlgMap.NormDistAlg.COSINE),
        JACCARD_DISTANCE(5, AlgMap.NormDistAlg.JACCARD),
        JARO_WINKLER_DISTANCE(6, AlgMap.NormDistAlg.JAROWRINKLER),
        METRIC_LCS_DISTANCE(7, AlgMap.NormDistAlg.METRICLCS),
        NGRAM_DISTANCE(8, AlgMap.NormDistAlg.NGRAM),
        N_LEVENSHTEIN_DISTANCE(9, AlgMap.NormDistAlg.NLEVENSHTEIN),
        SORENSEN_DICE_DISTANCE(10, AlgMap.NormDistAlg.SORENSENDICE),
        COSINE_SIMILARITY(11, AlgMap.NormSimAlg.COSINE),
        JACCARD_SIMILARITY(12, AlgMap.NormSimAlg.JACCARD),
        JARO_WINKLER_SIMILARITY(13, AlgMap.NormSimAlg.JAROWRINKLER),
        N_LEVENSHTEIN_SIMILARITY(14, AlgMap.NormSimAlg.NLEVENSHTEIN),
        SORENSEN_DICE_SIMILARITY(15, AlgMap.NormSimAlg.SORENSENDICE),
        DAMERAU_METRIC(16, AlgMap.MetricDistAlg.DAMERAU),
        JACCARD_METRIC(17, AlgMap.MetricDistAlg.JACCARD),
        LEVENSHTEIN_METRIC(18, AlgMap.MetricDistAlg.LEVENSHTEIN),
        METRIC_LCS_METRIC(19, AlgMap.MetricDistAlg.METRICLCS),
    }

    private val DEFAULT_QUICKCOMPARE_N = 3
    private val DEFAULT_DEADLINE = 0.45f

    private val SPLITTERS = arrayOf<String?>(Tuils.SPACE)
    private val FILE_SPLITTERS = arrayOf<String?>(Tuils.SPACE, "-", "_")
    private val XML_PREFS_SPLITTERS = arrayOf<String?>("_")

    private val showAliasDefault: Boolean
    private val clickToLaunch: Boolean
    private val showAppsGpDefault: Boolean
    private val minCmdPriority: Int

    private val rmQuotes: Pattern = Pattern.compile("['\"]")

    var suggestionsPerCategory: Int
    var suggestionsDeadline: Float

    var counts: Map<SuggestionCategory, Int>?
    var noInputCounts: Map<SuggestionCategory, Int>?

    val comparator: CustomComparator

    /** Set at the top of each [getSuggestions] call; long-running loops poll it to bail out
     * cooperatively once the caller (a coroutine cancelled by a newer keystroke) is no longer active. */
    private var activeContext: CoroutineContext = EmptyCoroutineContext

    private val alg: Alg = SimilarityAlgorithm.JARO_WINKLER_SIMILARITY.alg
    private val algInstance: Algorithm =
        alg.buildAlg(SimilarityAlgorithm.JARO_WINKLER_SIMILARITY.id)

    private val quickCompare: Int

    init {
        quickCompare = DEFAULT_QUICKCOMPARE_N

        this.suggestionsPerCategory = XMLPrefsManager.getInt(Suggestions.suggestions_per_category)
        this.suggestionsDeadline = DEFAULT_DEADLINE

        Suggestion.appendQuotesBeforeFile =
            XMLPrefsManager.getBoolean(Behavior.append_quote_before_file)

        showAliasDefault = XMLPrefsManager.getBoolean(Suggestions.suggest_alias_default)
        showAppsGpDefault = XMLPrefsManager.getBoolean(Suggestions.suggest_appgp_default)
        clickToLaunch = XMLPrefsManager.getBoolean(Suggestions.click_to_launch)

        minCmdPriority = XMLPrefsManager.getInt(Suggestions.noinput_min_command_priority)

        val orderEntries = SuggestionOrderConfig.load(pack.context)
        val inputEntries = orderEntries.filter { it.mode == SuggestionOrderMode.INPUT }
        val noInputEntries = orderEntries.filter { it.mode == SuggestionOrderMode.NO_INPUT }

        counts = inputEntries.associate { it.category to it.count }
        val indexes = inputEntries.associate { it.category to it.order }

        noInputCounts = noInputEntries.associate { it.category to it.count }
        val noInputIndexes = noInputEntries.associate { it.category to it.order }

        comparator = CustomComparator(noInputIndexes, indexes)
    }

    //    there's always a space between beforelastspace and lastword
    fun getSuggestions(
        beforeLastSpace: String,
        lastWord: String,
        coroutineContext: CoroutineContext = EmptyCoroutineContext
    ): MutableList<Suggestion?> {
        activeContext = coroutineContext

        val before = beforeLastSpace.trim { it <= ' ' }
        val word = lastWord.trim { it <= ' ' }
        val suggestionList: MutableList<Suggestion?> = ArrayList<Suggestion?>()

        comparator.noInput = before.isEmpty() && word.isEmpty()

        when {
            before.isEmpty() && word.isEmpty() -> suggestNoInput(suggestionList, before)
            before.isEmpty() -> suggestFirstWordCandidates(suggestionList, word)
            word.isEmpty() -> suggestForCommandContext(suggestionList, before, null)
            else -> suggestForCommandContext(suggestionList, before, word)
        }

        Collections.sort<Suggestion?>(suggestionList, comparator)
        return suggestionList
    }

    /** Nothing typed yet: default apps, all commands (subject to [minCmdPriority]), and (if enabled)
     * default aliases/app groups. */
    private fun suggestNoInput(suggestionList: MutableList<Suggestion?>, beforeLastSpace: String) {
        val apps = appsManager.suggestedApps
        if (apps != null) {
            var count = 0
            while (count < apps.size && count < (noInputCounts?.get(SuggestionCategory.APP) ?: 0)) {
                val app = apps[count]
                if (app == null) {
                    count++
                    continue
                }

                suggestionList.add(
                    Suggestion(beforeLastSpace, app.name(), clickToLaunch, SuggestionKind.App(app))
                )
                count++
            }
        }

        suggestCommand(pack, suggestionList, null)

        if (showAliasDefault) suggestAlias(aliasManager, suggestionList, Tuils.EMPTYSTRING)
        if (showAppsGpDefault) {
            suggestAppGroup(pack, suggestionList, Tuils.EMPTYSTRING, beforeLastSpace)
        }
    }

    /** Typing the first word of the input (nothing before it yet): candidate commands, aliases,
     * apps and app groups whose name starts with [lastWord]. */
    private fun suggestFirstWordCandidates(suggestionList: MutableList<Suggestion?>, lastWord: String) {
        suggestCommand(pack, suggestionList, lastWord, Tuils.EMPTYSTRING)
        suggestAlias(aliasManager, suggestionList, lastWord)
        suggestApp(pack, suggestionList, lastWord, Tuils.EMPTYSTRING)
        suggestAppGroup(pack, suggestionList, lastWord, Tuils.EMPTYSTRING)
    }

    /** Continuing an already-started command (`beforeLastSpace` is non-empty): its params/args if
     * it parses as a known [Command], otherwise a file or app suggestion. [lastWord] is null when
     * there's a trailing space with nothing typed yet, matching the nullable `afterLastSpace`
     * contract of [suggestArgs]/[suggestParams]. */
    private fun suggestForCommandContext(
        suggestionList: MutableList<Suggestion?>,
        beforeLastSpace: String,
        lastWord: String?,
    ) {
        val cmd = tryParseCommand(beforeLastSpace)
        if (cmd != null) {
            suggestForKnownCommand(cmd, suggestionList, beforeLastSpace, lastWord)
            return
        }

        if (isShellCommand(beforeLastSpace)) {
            suggestFile(pack, suggestionList, lastWord ?: Tuils.EMPTYSTRING, beforeLastSpace)
        } else if (!suggestAppInsideGroup(
                pack,
                suggestionList,
                lastWord ?: Tuils.EMPTYSTRING,
                beforeLastSpace,
                false
            )
        ) {
            suggestApp(
                pack,
                suggestionList,
                beforeLastSpace + Tuils.SPACE + (lastWord ?: Tuils.EMPTYSTRING),
                Tuils.EMPTYSTRING
            )
        }
    }

    private fun suggestForKnownCommand(
        cmd: Command,
        suggestionList: MutableList<Suggestion?>,
        beforeLastSpace: String,
        lastWord: String?,
    ) {
        if (cmd.cmd is PermanentSuggestionCommand) {
            suggestPermanentSuggestions(suggestionList, cmd.cmd as PermanentSuggestionCommand)
        }

        val cmdArgs = cmd.mArgs

        //        a trailing space right after a param command whose args are already all filled in:
        //        nothing left to suggest
        val alreadyComplete = lastWord == null && cmdArgs.isNotEmpty() && cmd.cmd is ParamCommand &&
            cmd.nArgs >= 1 && cmdArgs[0] is Param && (cmdArgs[0] as Param).args().size + 1 == cmd.nArgs
        if (alreadyComplete) return

        if (cmd.cmd is ParamCommand && (cmdArgs.isEmpty() || cmdArgs[0] is String)) {
            suggestParams(pack, suggestionList, cmd.cmd as ParamCommand, beforeLastSpace, lastWord)
        } else {
            suggestArgs(pack, cmd.nextArg(), suggestionList, lastWord, beforeLastSpace)
        }
    }

    private fun tryParseCommand(beforeLastSpace: String): Command? {
        return try {
            CommandTuils.parse(beforeLastSpace, pack, appsManager, contacts)
        } catch (e: Exception) {
            null
        }
    }

    private fun isShellCommand(beforeLastSpace: String): Boolean {
        val split = rmQuotes.matcher(beforeLastSpace).replaceAll(Tuils.EMPTYSTRING)
            .split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return split.any { needsFileSuggestion(it) }
    }

    private fun needsFileSuggestion(cmd: String): Boolean {
        return when (cmd.lowercase(Locale.getDefault())) {
            "ls", "cd", "mv", "cp", "rm", "cat" -> true
            else -> false
        }
    }

    private fun suggestPermanentSuggestions(
        suggestions: MutableList<Suggestion?>,
        cmd: PermanentSuggestionCommand
    ) {
        for (s in cmd.permanentSuggestions()) {
            val sugg = Suggestion(null, s, false, SuggestionKind.Permanent)
            suggestions.add(sugg)
        }
    }

    private fun suggestAlias(
        aliasManager: AliasManager,
        suggestions: MutableList<Suggestion?>,
        lastWord: String?
    ) {
        var canInsert =
            if (lastWord.isNullOrEmpty()) noInputCounts?.get(SuggestionCategory.ALIAS) ?: 0
            else counts?.get(SuggestionCategory.ALIAS) ?: 0

        for (a in aliasManager.getAliases(true)) {
            if (lastWord.isNullOrEmpty() || a.aliasName.startsWith(lastWord)) {
                if (canInsert == 0) return
                canInsert--

                suggestions.add(
                    Suggestion(
                        Tuils.EMPTYSTRING,
                        a.aliasName,
                        clickToLaunch && !a.isParametrized,
                        SuggestionKind.Alias
                    )
                )
            }
        }
    }

    private fun suggestParams(
        pack: CommandContext,
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
                        SuggestionKind.Param
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
                            SuggestionKind.Param
                        )
                    )
                }
            }
        }
    }

    private fun suggestArgs(
        info: CommandContext,
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
        info: CommandContext,
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
                SuggestionKind.Bool
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "false",
                clickToLaunch,
                SuggestionKind.Bool
            )
        )
    }

    private fun suggestFile(
        info: CommandContext,
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
                    SuggestionKind.File(afterLastSpace)
                )
            )
        }

        if (Suggestion.appendQuotesBeforeFile && !noAfterLastSpace && !afterLastSpace.endsWith(
                Suggestion.SINGLE_QUOTE
            ) && !afterLastSpace.endsWith(Suggestion.DOUBLE_QUOTES)
        ) suggestions.add(
            Suggestion(
                beforeLastSpace,
                Suggestion.SINGLE_QUOTE,
                false,
                SuggestionKind.File(afterLastSpace)
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
                    total.length - total.replace(Suggestion.DOUBLE_QUOTES, Tuils.EMPTYSTRING).replace(
                        Suggestion.SINGLE_QUOTE, Tuils.EMPTYSTRING
                    ).length

                if (quotesCount > 0) {
                    val singleQIndex: Int = total.lastIndexOf(Suggestion.SINGLE_QUOTE)
                    val doubleQIndex: Int = total.lastIndexOf(Suggestion.DOUBLE_QUOTES)

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

        val temp = rmQuotes.matcher(afterLastSeparator).replaceAll(Tuils.EMPTYSTRING)

        val counter = quickCompare(
            temp,
            files,
            suggestions,
            beforeLastSpace,
            suggestionsPerCategory,
            false
        ) { SuggestionKind.File(null) }
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
                    SuggestionKind.File(afterLastSpaceWithoutALS)
                )
            )
        }
    }

    /** [kindOf] builds the [SuggestionKind] for a match; it decides for itself whether to embed
     * the matched [String] (most callers ignore it and return a fixed kind). */
    private fun quickCompare(
        s1: String,
        ss: Array<String>,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        max: Int,
        exec: Boolean,
        kindOf: (matched: String) -> SuggestionKind
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
                        kindOf(ss[c])
                    )
                )

                ss[c] = Tuils.EMPTYSTRING

                counter++
            }
        }

        return counter
    }

    /** [kindOf] builds the [SuggestionKind] for a match, given the matched [StringableObject] -
     * callers cast it to their concrete element type when they want to embed it. */
    private fun quickCompare(
        s1: String,
        ss: MutableList<out StringableObject>,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?,
        max: Int,
        exec: Boolean,
        kindOf: (matched: StringableObject) -> SuggestionKind
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
                        kindOf(o)
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
                        SuggestionKind.File(afterLastSpaceHolder)
                    )
                )
            }
        } catch (e: NullPointerException) {
            LogTuils.log(e)
        }
    }

    private fun suggestContact(
        info: CommandContext,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val contactList = contacts?.getContacts()
        if (contactList == null || contactList.size == 0) return

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (contact in contactList) suggestions.add(
                Suggestion(
                    beforeLastSpace,
                    contact.name,
                    true,
                    SuggestionKind.Contact(contact)
                )
            )
        } else {
            val counter = quickCompare(
                afterLastSpace,
                contactList,
                suggestions,
                beforeLastSpace,
                suggestionsPerCategory,
                true
            ) { matched -> SuggestionKind.Contact(matched as Contact) }
            if (suggestionsPerCategory - counter <= 0) return

            val cts = CompareObjects.topMatchesWithDeadline<Contact?>(
                Contact::class.java,
                afterLastSpace,
                contactList.size,
                contactList,
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
                        SuggestionKind.Contact(c)
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
                SuggestionKind.Bool
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "xpath",
                false,
                SuggestionKind.Bool
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "format",
                false,
                SuggestionKind.Bool
            )
        )
    }

    private fun suggestCommand(
        info: CommandContext,
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

            var canInsert = counts?.get(SuggestionCategory.COMMAND) ?: 0
            for (s in cmds) {
                if (canInsert == 0) return
                activeContext.ensureActive()

                if (s.startsWith(afterLastSpace)) {
                    val cmd = info.commandGroup.getCommandByName(s)
                    val args = cmd.argType()
                    val exec = args == null || args.size == 0
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            s,
                            exec && clickToLaunch,
                            SuggestionKind.Command
                        )
                    )
                    canInsert--
                }
            }
        }
    }

    private fun suggestCommand(
        info: CommandContext,
        suggestions: MutableList<Suggestion?>,
        beforeLastSpace: String?
    ) {
        val cmds = info.commandGroup.getCommands()
        if (cmds == null) return

        //        if there's a beforelastspace -> help ...
        var canInsert =
            if (beforeLastSpace != null && beforeLastSpace.length > 0) Int.MAX_VALUE else noInputCounts?.get(SuggestionCategory.COMMAND) ?: 0

        for (cmd in cmds) {
            if (canInsert == 0) return
            activeContext.ensureActive()

            if (info.cmdPrefs.getPriority(cmd) >= minCmdPriority) {
                val args = cmd.argType()
                val exec = args == null || args.size == 0

                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        cmd.javaClass.getSimpleName(),
                        exec && clickToLaunch,
                        SuggestionKind.Command
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
                    SuggestionKind.Color
                )
            )
        }
    }

    private fun suggestApp(
        info: CommandContext,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        suggestApp(appsManager.shownApps(), suggestions, afterLastSpace, beforeLastSpace, true)
    }

    private fun suggestHiddenApp(
        info: CommandContext,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        suggestApp(
            appsManager.hiddenApps(),
            suggestions,
            afterLastSpace,
            beforeLastSpace,
            false
        )
    }

    private fun suggestAllPackages(
        info: CommandContext,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val apps: MutableList<Launchable> = ArrayList<Launchable>(appsManager.shownApps())
        apps.addAll(appsManager.hiddenApps())
        suggestApp(apps, suggestions, afterLastSpace, beforeLastSpace, true)
    }

    private fun suggestDefaultApp(
        info: CommandContext,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "most_used",
                false,
                SuggestionKind.Permanent
            )
        )
        suggestions.add(
            Suggestion(
                beforeLastSpace,
                "null",
                false,
                SuggestionKind.Permanent
            )
        )

        suggestApp(appsManager.shownApps(), suggestions, afterLastSpace, beforeLastSpace, true)
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

        var canInsert = counts?.get(SuggestionCategory.APP) ?: 0
        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (l in apps!!) {
                if (canInsert == 0) return
                canInsert--

                suggestions.add(
                    Suggestion(
                        beforeLastSpace,
                        l.name(),
                        canClickToLaunch && clickToLaunch,
                        SuggestionKind.App(l)
                    )
                )
            }
        } else {
            val embed = canClickToLaunch && clickToLaunch
            val counter = quickCompare(
                afterLastSpace,
                apps!!,
                suggestions,
                beforeLastSpace,
                canInsert,
                embed
            ) { matched -> SuggestionKind.App(if (embed) matched as Launchable else null) }
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
                        i.name(),
                        canClickToLaunch && clickToLaunch,
                        SuggestionKind.App(if (canClickToLaunch && clickToLaunch) i else null)
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
            Collections.addAll<Reply?>(CommandTuils.xmlPrefsEntrys, *Reply.entries.toTypedArray())
        }

        val list: MutableList<XMLPrefsSave> = ArrayList<XMLPrefsSave>(CommandTuils.xmlPrefsEntrys)

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (s in list) {
                val sg =
                    Suggestion(beforeLastSpace, s.label(), false, SuggestionKind.Command)
                suggestions.add(sg)
            }
        } else {
            val counter = quickCompare(
                afterLastSpace,
                list,
                suggestions,
                beforeLastSpace,
                suggestionsPerCategory,
                false
            ) { SuggestionKind.Command }
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
                        SuggestionKind.Command
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
        }

        if (afterLastSpace == null || afterLastSpace.length == 0) {
            for (s in CommandTuils.xmlPrefsFiles) {
                val sg = Suggestion(
                    beforeLastSpace,
                    s,
                    false,
                    SuggestionKind.ConfigFile(afterLastSpace)
                )
                suggestions.add(sg)
            }
        } else if (afterLastSpace.length <= FIRST_INTERVAL) {
            afterLastSpace = afterLastSpace.trim { it <= ' ' }.lowercase(Locale.getDefault())
            for (s in CommandTuils.xmlPrefsFiles) {
                activeContext.ensureActive()

                if (s.startsWith(afterLastSpace)) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            s,
                            false,
                            SuggestionKind.ConfigFile(afterLastSpace)
                        )
                    )
                }
            }
        }
    }

    private fun suggestAppGroup(
        pack: CommandContext,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String?
    ) {
        val groups: MutableList<AppsManager.Group> =
            ArrayList<AppsManager.Group>(appsManager.groups)
        if (groups.size == 0) return

        var canInsert: Int
        if (afterLastSpace == null || afterLastSpace.length == 0) {
            canInsert = noInputCounts?.get(SuggestionCategory.APP_GROUP) ?: 0
            for (g in groups) {
                if (canInsert == 0) return
                canInsert--

                val sg = Suggestion(
                    beforeLastSpace,
                    g.name() ?: continue,
                    false,
                    SuggestionKind.AppGroup(g)
                )
                suggestions.add(sg)
            }
        } else {
            canInsert = counts?.get(SuggestionCategory.APP_GROUP) ?: 0

            val counter = quickCompare(
                afterLastSpace,
                groups,
                suggestions,
                beforeLastSpace,
                canInsert,
                false
            ) { matched -> SuggestionKind.AppGroup(matched as AppsManager.Group) }
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
                        SuggestionKind.AppGroup(g)
                    )
                )
            }
        }
    }

    private fun suggestAppInsideGroup(
        pack: CommandContext,
        suggestions: MutableList<Suggestion?>,
        afterLastSpace: String?,
        beforeLastSpace: String,
        keepGroupName: Boolean
    ): Boolean {
        var beforeLastSpace = beforeLastSpace
        var index = -1

        var app: String? = Tuils.EMPTYSTRING

        if (!beforeLastSpace.contains(Tuils.SPACE)) {
            index = StringTuils.find(beforeLastSpace, appsManager.groups)
            app = afterLastSpace
            if (!keepGroupName) beforeLastSpace = Tuils.EMPTYSTRING
        } else {
            val split = beforeLastSpace.split(Tuils.SPACE.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()
            var count = 0
            while (count < split.size) {
                index = StringTuils.find(split[count], appsManager.groups)
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

        val g = appsManager.groups.get(index)

        val apps: MutableList<Launchable> =
            ArrayList((g.members() as? Collection<Launchable>) ?: emptyList())
        if (apps.size > 0) {
            if (app == null || app.length == 0) {
                for (o in apps) {
                    suggestions.add(
                        Suggestion(
                            beforeLastSpace,
                            o.name(),
                            clickToLaunch,
                            SuggestionKind.App(o)
                        )
                    )
                }
            } else {
                val counter = quickCompare(
                    app,
                    apps,
                    suggestions,
                    beforeLastSpace,
                    Int.MAX_VALUE,
                    clickToLaunch
                ) { matched -> SuggestionKind.App(matched as Launchable) }
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
                            gli.name(),
                            clickToLaunch,
                            SuggestionKind.App(gli)
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
                        SuggestionKind.BoundReplyApp(null)
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
                false
            ) { SuggestionKind.BoundReplyApp(null) }
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
                        SuggestionKind.BoundReplyApp(null)
                    )
                )
            }
        }

        return true
    }

    class CustomComparator(
        var noInputIndexes: Map<SuggestionCategory, Int>,
        var inputIndexes: Map<SuggestionCategory, Int>,
    ) : Comparator<Suggestion?> {
        var noInput: Boolean = false

        override fun compare(o1: Suggestion?, o2: Suggestion?): Int {
            if (o1 == null || o2 == null) return 0

            val c1 = o1.kind.category
            val c2 = o2.kind.category
            if (c1 == c2) return 0

            return when {
                noInput -> noInputIndexes.getValue(c1) - noInputIndexes.getValue(c2)
                inputIndexes.containsKey(c1) && inputIndexes.containsKey(c2) ->
                    inputIndexes.getValue(c1) - inputIndexes.getValue(c2)
                inputIndexes.containsKey(c1) -> -1
                inputIndexes.containsKey(c2) -> 1
                else -> 0
            }
        }
    }
}
