package ohi.andre.consolelauncher.commands.main.specific

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.tuils.SimpleMutableEntry
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.LogTuils

abstract class ParamCommand : CommandAbstraction {

    final override fun argType(): IntArray = intArrayOf(CommandAbstraction.PARAM)

    // Legacy String? path kept for the many Java callers still on CommandAbstraction.exec();
    // the real dispatch logic lives in execResult() now.
    final override fun exec(pack: CommandContext): String? = CommandResult.toLegacyString(execResult(pack))

    final override fun execResult(pack: CommandContext): CommandResult {
        val o = doThings(pack)
        if (o != null) return CommandResult.Output(o)

        val param = pack.get(Param::class.java)
        if (param == null) {
            val o1 = pack.get(Any::class.java, 0)

            return if (o1 == null || o1.toString().isEmpty()) CommandResult.Output(pack.context.getString(helpRes()))
            else CommandResult.Error(pack.context.getString(R.string.output_invalid_param) + Tuils.SPACE + o1.toString())
        }
        return param.execResult(pack)
    }

    open fun getParam(pack: CommandContext, param: String): SimpleMutableEntry<Boolean, Param?> {
        val p = paramForString(pack, param)
        if (p == null && defaultParamReference() != null) {
            return SimpleMutableEntry(true, paramForString(pack, defaultParam(pack)))
        }
        return SimpleMutableEntry(false, p)
    }

    open fun defaultParam(pack: CommandContext): String {
        var def = pack.cmdPrefs.get(defaultParamReference())
        if (!def.startsWith("-")) def = "-$def"
        return def
    }

    override fun onNotArgEnough(pack: CommandContext, nArgs: Int): String {
        return pack.context.getString(helpRes())
    }

    override fun onArgNotFound(pack: CommandContext, indexNotFound: Int): String {
        LogTuils.log("inf", indexNotFound)
        if (indexNotFound == 0) {
            try {
                LogTuils.log("last")
                val param = pack.get(String::class.java, 0)
                return pack.context.getString(R.string.output_invalid_param) + Tuils.SPACE + param
            } catch (e: Exception) {
            }
        }

        return pack.context.getString(helpRes())
    }

    abstract fun params(): Array<String>
    protected abstract fun paramForString(pack: CommandContext, param: String): Param?
    protected abstract fun doThings(pack: CommandContext): String?
    open fun defaultParamReference(): XMLPrefsSave? = null
}
