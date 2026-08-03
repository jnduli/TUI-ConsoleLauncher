package ohi.andre.consolelauncher.commands.main.raw

import android.media.AudioManager
import android.media.ToneGenerator
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult

class beep : CommandAbstraction {

    @Throws(Exception::class)
    override fun exec(pack: CommandContext): String? = CommandResult.toLegacyString(execResult(pack))

    @Throws(Exception::class)
    override fun execResult(pack: CommandContext): CommandResult {
        try {
            val toneG = ToneGenerator(AudioManager.STREAM_ALARM, 50)
            toneG.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
        } catch (e: Exception) {
            return CommandResult.Error(e.toString())
        }

        return CommandResult.Empty
    }

    override fun argType(): IntArray = IntArray(0)

    override fun priority(): Int = 2

    override fun helpRes(): Int = R.string.help_beep

    override fun onNotArgEnough(pack: CommandContext, nArgs: Int): String? =
        CommandResult.toLegacyString(onNotArgEnoughResult(pack, nArgs))

    override fun onNotArgEnoughResult(pack: CommandContext, nArgs: Int): CommandResult =
        CommandResult.Error(pack.context.getString(helpRes()))
}
