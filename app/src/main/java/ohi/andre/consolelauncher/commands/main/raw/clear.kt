package ohi.andre.consolelauncher.commands.main.raw

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult

class clear : CommandAbstraction {

    @Throws(Exception::class)
    override fun exec(pack: CommandContext): String? = CommandResult.toLegacyString(execResult(pack))

    @Throws(Exception::class)
    override fun execResult(pack: CommandContext): CommandResult {
        LocalBroadcastManager.getInstance(pack.context.applicationContext).sendBroadcast(Intent(UIManager.ACTION_CLEAR))
        return CommandResult.Empty
    }

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 2

    override fun helpRes(): Int = R.string.help_clear
}
