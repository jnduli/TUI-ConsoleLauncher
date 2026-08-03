package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.tuils.SystemUtils

class tutorial : CommandAbstraction {

    private val url = "https://github.com/Andre1299/TUI-ConsoleLauncher/wiki"

    @Throws(Exception::class)
    override fun exec(pack: CommandContext): String? = CommandResult.toLegacyString(execResult(pack))

    @Throws(Exception::class)
    override fun execResult(pack: CommandContext): CommandResult {
        pack.context.startActivity(SystemUtils.webPage(url))
        return CommandResult.Empty
    }

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 4

    override fun helpRes(): Int = R.string.help_tutorial
}
