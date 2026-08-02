package ohi.andre.consolelauncher.commands.main.specific

import ohi.andre.consolelauncher.commands.CommandAbstraction
import ohi.andre.consolelauncher.commands.CommandContext

abstract class RedirectCommand : CommandAbstraction {

    @JvmField
    val beforeObjects: MutableList<Any> = ArrayList()
    @JvmField
    val afterObjects: MutableList<Any> = ArrayList()

    abstract fun onRedirect(pack: CommandContext): String
    abstract fun getHint(): Int
    abstract fun isWaitingPermission(): Boolean

    open fun cleanup() {
        beforeObjects.clear()
        afterObjects.clear()
    }
}
