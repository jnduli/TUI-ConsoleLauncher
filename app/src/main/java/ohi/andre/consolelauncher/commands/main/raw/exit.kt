package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.tuils.SystemUtils

class exit : CommandAbstraction {
    @Throws(Exception::class)
    override fun exec(pack: CommandContext): String? = CommandResult.toLegacyString(execResult(pack))

    @Throws(Exception::class)
    override fun execResult(pack: CommandContext): CommandResult {
        SystemUtils.resetPreferredLauncherAndOpenChooser(pack.context)
        return CommandResult.Empty
    }

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_exit
}
