package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.managers.TimeManager

class time : CommandAbstraction {
    override fun exec(pack: CommandContext): String? = CommandResult.toLegacyString(execResult(pack))

    override fun execResult(pack: CommandContext): CommandResult {
        val index = pack.getInt()
        return CommandResult.Output(TimeManager.instance.replace("%t$index").toString())
    }

    override fun priority(): Int = 4

    override fun argType(): IntArray = intArrayOf(CommandAbstraction.INT)

    override fun helpRes(): Int = R.string.help_time

    override fun onArgNotFound(pack: CommandContext, indexNotFound: Int): String? =
        CommandResult.toLegacyString(onArgNotFoundResult(pack, indexNotFound))

    override fun onArgNotFoundResult(pack: CommandContext, indexNotFound: Int): CommandResult =
        CommandResult.Error(pack.context.getString(R.string.invalid_integer))

    override fun onNotArgEnough(pack: CommandContext, nArgs: Int): String? =
        CommandResult.toLegacyString(onNotArgEnoughResult(pack, nArgs))

    // Not an error case: the default "%t0" time is legitimate output, unlike the base
    // interface's default which wraps onNotArgEnough() as CommandResult.Error.
    override fun onNotArgEnoughResult(pack: CommandContext, nArgs: Int): CommandResult =
        CommandResult.Output(TimeManager.instance.replace("%t0").toString())
}
