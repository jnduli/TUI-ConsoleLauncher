package ohi.andre.consolelauncher.commands

import ohi.andre.consolelauncher.commands.main.Param
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.tuils.LogTuils
import ohi.andre.consolelauncher.tuils.StringTuils
import ohi.andre.consolelauncher.tuils.Tuils

/** Turns raw command-line input into a [Command]: resolves the command name, then its args. */
object CommandParser {

    fun parse(rawInput: String, info: CommandContext, appsManager: AppsManager, contacts: ContactManager?): Command? {
        val command = Command()

        val name = findName(rawInput)
        if (!StringTuils.isAlpha(name)) return null

        val cmd = info.commandGroup.getCommandByName(name) ?: return null
        command.cmd = cmd

        var input: String? = rawInput.substring(name.length).trim()

        val args = ArrayList<Any?>()
        var nArgs = 0
        var types: IntArray?

        try {
            if (cmd is ParamCommand) {
                val arg = param(info, cmd, input)
                if (arg == null || !arg.found) {
                    command.parseOutcome = Command.ParseOutcome.InvalidParam
                    args.add(input)
                    command.nArgs = 1
                    command.mArgs = args.toTypedArray()
                    return command
                }

                input = arg.residualString
                val p = arg.arg as Param
                types = p.args()

                nArgs++
                args.add(p)
            } else {
                types = cmd.argType()
            }

            if (types != null) {
                for (count in types.indices) {
                    if (input == null) break

                    input = input.trim()
                    if (input.isEmpty()) break

                    val arg = getArg(info, input, types[count], appsManager, contacts) ?: return null

                    if (!arg.found) {
                        command.parseOutcome = Command.ParseOutcome.ArgNotFound(if (cmd is ParamCommand) count + 1 else count)
                        args.add(input)
                        command.mArgs = args.toTypedArray()
                        command.nArgs = nArgs
                        return command
                    }

                    nArgs += arg.n
                    args.add(arg.arg)
                    input = arg.residualString
                }
            }
        } catch (e: Exception) {
            LogTuils.log(e)
        }

        command.mArgs = args.toTypedArray()
        command.nArgs = nArgs

        return command
    }

    private fun findName(input: String): String {
        val space = input.indexOf(Tuils.SPACE)
        return if (space == -1) input else input.substring(0, space)
    }

    private fun getArg(
        info: CommandContext,
        input: String,
        type: Int,
        appsManager: AppsManager,
        contacts: ContactManager?
    ): ArgInfo? = when (type) {
        CommandAbstraction.FILE -> FileArgResolver.file(input, info.currentDirectory)
        CommandAbstraction.CONTACTNUMBER -> ArgExtractors.contactNumber(input, contacts)
        CommandAbstraction.PLAIN_TEXT -> ArgExtractors.plainText(input)
        CommandAbstraction.VISIBLE_PACKAGE -> ArgExtractors.activityName(input, appsManager)
        CommandAbstraction.HIDDEN_PACKAGE -> ArgExtractors.hiddenPackage(input, appsManager)
        CommandAbstraction.TEXTLIST -> ArgExtractors.textList(input)
        CommandAbstraction.COMMAND -> ArgExtractors.command(input, info.commandGroup)
        CommandAbstraction.BOOLEAN -> ArgExtractors.bln(input)
        CommandAbstraction.COLOR -> ArgExtractors.color(input)
        CommandAbstraction.CONFIG_ENTRY -> ConfigEntryLookup.configEntry(input)
        CommandAbstraction.CONFIG_FILE -> ConfigEntryLookup.configFile(input)
        CommandAbstraction.INT -> ArgExtractors.integer(input)
        CommandAbstraction.DEFAULT_APP -> ArgExtractors.defaultApp(input, appsManager)
        CommandAbstraction.ALL_PACKAGES -> ArgExtractors.allPackages(input, appsManager)
        CommandAbstraction.NO_SPACE_STRING,
        CommandAbstraction.APP_GROUP,
        CommandAbstraction.BOUND_REPLY_APP -> ArgExtractors.noSpaceString(input)
        CommandAbstraction.APP_INSIDE_GROUP -> ArgExtractors.activityName(input, appsManager)
        CommandAbstraction.LONG -> ArgExtractors.numberLong(input)
        CommandAbstraction.DATASTORE_PATH_TYPE -> ArgExtractors.dataStoreType(input)
        else -> null
    }

    private fun param(pack: CommandContext, cmd: ParamCommand, input: String?): ArgInfo? {
        if (input == null || input.trim().isEmpty()) return null

        var indexOfFirstSpace = input.indexOf(Tuils.SPACE)
        if (indexOfFirstSpace == -1) indexOfFirstSpace = input.length

        var param = input.substring(0, indexOfFirstSpace).trim()
        if (!param.startsWith("-")) param = "-$param"

        val sm = cmd.getParam(pack, param)
        val p = sm.value
        val df = sm.key

        return ArgInfo(p, if (df) input else input.substring(indexOfFirstSpace), p != null, if (p != null) 1 else 0)
    }
}
