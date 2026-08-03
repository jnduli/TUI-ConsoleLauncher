package ohi.andre.consolelauncher.commands.main.raw

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.managers.ChangelogManager
import okhttp3.OkHttpClient

class changelog(private val client: OkHttpClient) : CommandAbstraction {

    @Throws(Exception::class)
    override fun exec(pack: CommandContext): String? = CommandResult.toLegacyString(execResult(pack))

    @Throws(Exception::class)
    override fun execResult(pack: CommandContext): CommandResult {
        ChangelogManager.printLog(pack.context, client, true)
        return CommandResult.Empty
    }

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 2

    override fun helpRes(): Int = R.string.help_changelog
}
