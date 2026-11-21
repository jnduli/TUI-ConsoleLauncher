package ohi.andre.consolelauncher.cli.builtin

import ohi.andre.consolelauncher.cli.ActionKind
import ohi.andre.consolelauncher.cli.ActionResolver
import ohi.andre.consolelauncher.cli.CliAction
import ohi.andre.consolelauncher.commands.Command
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandParser
import ohi.andre.consolelauncher.commands.CommandResult

/** Runs one of the built-in T-UI commands (the CommandAbstraction classes). */
class TuiBuiltinAction(
    private val command: Command,
    private val ctx: CommandContext,
) : CliAction {
    override val kind = ActionKind.TUI_BUILTIN
    override val name: String = command.cmd.javaClass.simpleName

    override fun run(): CommandResult = command.execResult(ctx)
}

class TuiBuiltinResolver(
    private val ctx: CommandContext,
    private val parser: CommandParser,
) : ActionResolver {
    override fun resolve(input: String): CliAction? {
        val command = parser.parse(input, ctx) ?: return null
        ctx.lastCommand = input
        return TuiBuiltinAction(command, ctx)
    }
}
