package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable

class restart : CommandAbstraction {

    override fun exec(pack: CommandContext): String? = CommandResult.toLegacyString(execResult(pack))

    override fun execResult(pack: CommandContext): CommandResult {
        (pack.context as Reloadable).reload()
        return CommandResult.Output(pack.context.getString(R.string.restarting))
    }

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_restart
}
