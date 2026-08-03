package ohi.andre.consolelauncher.commands.main.raw

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult

class rate : CommandAbstraction {

    override fun exec(pack: CommandContext): String? = CommandResult.toLegacyString(execResult(pack))

    override fun execResult(pack: CommandContext): CommandResult {
        try {
            pack.context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pack.context.packageName))
            )
        } catch (anfe: ActivityNotFoundException) {
            pack.context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pack.context.packageName))
            )
        }

        return CommandResult.Output(pack.res.getString(R.string.output_rate))
    }

    override fun helpRes(): Int = R.string.help_rate

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 3
}
