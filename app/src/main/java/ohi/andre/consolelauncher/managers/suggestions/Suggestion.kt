package ohi.andre.consolelauncher.managers.suggestions

import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.ContactManager.Contact
import ohi.andre.consolelauncher.managers.launchable.Launchable
import ohi.andre.consolelauncher.managers.notifications.reply.BoundApp
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File

enum class SuggestionCategory(val configKey: String) {
    APP("app"),
    ALIAS("alias"),
    COMMAND("cmd"),
    APP_GROUP("appgroup"),

    FILE("file"),
    BOOLEAN("boolean"),
    CONTACT("contact"),
    COLOR("color"),
    PERMANENT("permanent"),
    CONFIG_FILE("configfile"),
}

sealed class SuggestionKind(val category: SuggestionCategory) {
    data class App(val launchable: Launchable? = null) : SuggestionKind(SuggestionCategory.APP)

    /** A command parameter name (e.g. `--verbose`) - grouped with [App] for sorting/coloring. */
    object Param : SuggestionKind(SuggestionCategory.APP)

    data class BoundReplyApp(val app: BoundApp? = null) : SuggestionKind(SuggestionCategory.APP)

    object Alias : SuggestionKind(SuggestionCategory.ALIAS)

    /** A command name, or (reused) a config-entry label from `suggestConfigEntry`. */
    object Command : SuggestionKind(SuggestionCategory.COMMAND)

    data class AppGroup(val group: AppsManager.Group) : SuggestionKind(SuggestionCategory.APP_GROUP)

    data class File(val pathContext: String? = null) : SuggestionKind(SuggestionCategory.FILE)

    /** A boolean literal, or (reused) one of the fixed datastore-type choices. */
    object Bool : SuggestionKind(SuggestionCategory.BOOLEAN)

    data class Contact(val contact: ohi.andre.consolelauncher.managers.ContactManager.Contact) :
        SuggestionKind(SuggestionCategory.CONTACT)

    object Color : SuggestionKind(SuggestionCategory.COLOR)
    object Permanent : SuggestionKind(SuggestionCategory.PERMANENT)
    data class ConfigFile(val pathContext: String? = null) : SuggestionKind(SuggestionCategory.CONFIG_FILE)
}

data class Suggestion(
    val textBefore: String?,
    val text: String,
    val exec: Boolean,
    val kind: SuggestionKind,
) {
    fun resolvedText(): String {
        return when (kind) {
            is SuggestionKind.Contact -> {
                val c = kind.contact

                if (c.numbers.size <= c.getSelectedNumber()) c.setSelectedNumber(0)

                textBefore + Tuils.SPACE + c.numbers.get(c.getSelectedNumber())
            }

            is SuggestionKind.Permanent -> text

            is SuggestionKind.File -> {
                val lastWord = kind.pathContext ?: Tuils.EMPTYSTRING

                val textIsSpecial =
                    (text == File.separator || text == DOUBLE_QUOTES || text == SINGLE_QUOTE)
                val appendLastWord = lastWord.endsWith(File.separator) || textIsSpecial

                textBefore +
                        Tuils.SPACE +
                        (if (appendLastWord) lastWord else Tuils.EMPTYSTRING) +
                        (if (appendQuotesBeforeFile && !appendLastWord) SINGLE_QUOTE else Tuils.EMPTYSTRING) +
                        text
            }

            else -> if (textBefore.isNullOrEmpty()) text else textBefore + Tuils.SPACE + text
        }
    }

    override fun toString(): String = text

    companion object {
        const val SINGLE_QUOTE: String = "'"
        const val DOUBLE_QUOTES: String = "\""

        var appendQuotesBeforeFile: Boolean = false
    }
}
