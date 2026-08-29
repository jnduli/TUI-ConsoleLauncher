package ohi.andre.consolelauncher.commands

import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.tuils.Tuils

/**
 * Built by [CommandParser.parse], which always assigns [cmd] and [mArgs] via direct field access
 * before returning the instance to any caller. `lateinit` documents that these have no meaningful
 * default rather than modeling "unset" as a null every reader has to keep checking for; a caller
 * that somehow got a [Command] before parsing finished would hit [kotlin.UninitializedPropertyAccessException]
 * instead of a silent NPE.
 */
class Command {

    /** Where [CommandParser.parse] gave up, if it did. */
    sealed class ParseOutcome {
        object Ok : ParseOutcome()

        /** ParamCommand-only: the first token wasn't a recognized `-flag` at all. */
        object InvalidParam : ParseOutcome()

        /** A later argument at [index] didn't parse against its expected type. */
        data class ArgNotFound(val index: Int) : ParseOutcome()
    }

    lateinit var cmd: CommandAbstraction
    lateinit var mArgs: Array<Any?>
    @JvmField var nArgs: Int = 0

    @JvmField var parseOutcome: ParseOutcome = ParseOutcome.Ok

    @Throws(Exception::class)
    fun exec(info: CommandContext): String? = CommandResult.toLegacyString(execResult(info))

    @Throws(Exception::class)
    fun execResult(info: CommandContext): CommandResult {
        info.set(mArgs)
        return validationError(cmd, info) ?: cmd.execResult(info)
    }

    private fun validationError(cmd: CommandAbstraction, info: CommandContext): CommandResult? =
        if (cmd is ParamCommand) paramValidationError(cmd, info) else plainValidationError(cmd, info)

    private fun paramValidationError(cmd: ParamCommand, info: CommandContext): CommandResult? {
        val outcome = parseOutcome
        if (outcome == ParseOutcome.InvalidParam) {
            return CommandResult.errorOf(info.context.getString(R.string.output_invalid_param) + Tuils.SPACE + mArgs[0])
        }

        val param = mArgs[0] as Param
        if (outcome is ParseOutcome.ArgNotFound) {
            return param.onArgNotFoundResult(info, outcome.index)
        }

        val minArgsNeeded = param.args().size + if (cmd.defaultParamReference() != null) 0 else 1
        if (nArgs < minArgsNeeded) {
            return param.onNotArgEnoughResult(info, nArgs)
        }

        return null
    }

    private fun plainValidationError(cmd: CommandAbstraction, info: CommandContext): CommandResult? {
        val outcome = parseOutcome
        if (outcome is ParseOutcome.ArgNotFound) {
            return cmd.onArgNotFoundResult(info, outcome.index)
        }

        val argTypes = cmd.argType()!!
        if (nArgs < argTypes.size) {
            return cmd.onNotArgEnoughResult(info, nArgs)
        }

        return null
    }

    fun nextArg(): Int {
        val useParamArgs = cmd is ParamCommand && mArgs.isNotEmpty()

        val args: IntArray? = if (useParamArgs) {
            (mArgs[0] as? Param)?.args()
        } else {
            cmd.argType()
        }

        if (args == null || args.isEmpty()) {
            return 0
        }

        val index = if (useParamArgs) nArgs - 1 else nArgs
        return if (index in args.indices) args[index] else 0
    }
}
