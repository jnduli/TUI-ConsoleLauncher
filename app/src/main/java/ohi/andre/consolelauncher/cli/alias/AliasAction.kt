package ohi.andre.consolelauncher.cli.alias

import android.content.Context
import ohi.andre.consolelauncher.cli.ActionKind
import ohi.andre.consolelauncher.cli.ActionResolver
import ohi.andre.consolelauncher.cli.CliAction
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.managers.AliasManager
import ohi.andre.consolelauncher.tuils.OutputTuils
import ohi.andre.consolelauncher.tuils.StringTuils

/**
 * Expands an alias and feeds the result back into the CLI as new input via [reenter]. If [echo]
 * is set, it's printed first so the user sees what the alias expanded to.
 */
class AliasAction(
    private val context: Context,
    override val name: String,
    private val expanded: String,
    private val echo: Pair<String, Int>?,
    private val reenter: (String) -> Unit,
) : CliAction {
    override val kind = ActionKind.ALIAS

    override fun run(): CommandResult {
        echo?.let { (text, color) -> OutputTuils.sendOutput(context, text, color) }
        reenter(expanded)
        return CommandResult.Empty
    }
}

/** @param echoColor colour for echoing the expanded alias, or null to not echo (show_alias_content). */
class AliasResolver(
    private val aliasManager: AliasManager,
    private val context: Context,
    private val echoColor: Int?,
    private val reenter: (String) -> Unit,
) : ActionResolver {
    override fun resolve(input: String): CliAction? {
        val alias = aliasManager.getAlias(input, true)
        val value = alias[0] ?: return null
        val name = alias[1] ?: return null
        val expanded = aliasManager.format(value, alias[2].orEmpty())
        val echo = echoColor?.let { aliasManager.formatLabel(name, StringTuils.removeUnncesarySpaces(expanded)) to it }
        return AliasAction(context, name, expanded, echo, reenter)
    }
}
