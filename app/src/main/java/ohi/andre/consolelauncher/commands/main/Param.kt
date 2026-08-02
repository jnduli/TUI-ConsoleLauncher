package ohi.andre.consolelauncher.commands.main

import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult

interface Param {

    fun args(): IntArray
    fun exec(pack: CommandContext): String?
    fun label(): String

    fun onNotArgEnough(pack: CommandContext, n: Int): String?
    fun onArgNotFound(pack: CommandContext, index: Int): String?

    /**
     * Richer counterpart to [exec]. Defaults to adapting [exec]'s legacy `String?` return, so
     * existing Java Param implementations need no changes; Kotlin ones can override this instead.
     */
    fun execResult(pack: CommandContext): CommandResult = CommandResult.of(exec(pack))

    fun onNotArgEnoughResult(pack: CommandContext, n: Int): CommandResult =
        CommandResult.errorOf(onNotArgEnough(pack, n))

    fun onArgNotFoundResult(pack: CommandContext, index: Int): CommandResult =
        CommandResult.errorOf(onArgNotFound(pack, index))

    companion object {
        /**
         * Shared lookup for commands whose Param "enum" was rewritten as plain instances (instead of
         * a real enum) so its members can close over per-command constructor-injected state.
         */
        @JvmStatic
        fun find(values: Array<out Param>, p: String): Param? {
            val lower = p.lowercase()
            return values.firstOrNull { lower.endsWith(it.label()) }
        }

        @JvmStatic
        fun labels(values: Array<out Param>): Array<String> = Array(values.size) { values[it].label() }
    }
}
