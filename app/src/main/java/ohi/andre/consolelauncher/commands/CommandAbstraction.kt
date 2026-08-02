package ohi.andre.consolelauncher.commands

interface CommandAbstraction {
    @Throws(Exception::class)
    fun exec(pack: CommandContext?): String?
    fun argType(): IntArray?
    fun priority(): Int
    fun helpRes(): Int
    fun onArgNotFound(pack: CommandContext?, indexNotFound: Int): String?
    fun onNotArgEnough(pack: CommandContext?, nArgs: Int): String?

    /**
     * Richer counterpart to [exec]. Defaults to adapting [exec]'s legacy `String?` return, so
     * existing Java commands need no changes; Kotlin commands can override this instead to
     * distinguish output/empty/error explicitly.
     */
    @Throws(Exception::class)
    fun execResult(pack: CommandContext?): CommandResult = CommandResult.of(exec(pack))

    fun onArgNotFoundResult(pack: CommandContext?, indexNotFound: Int): CommandResult =
        CommandResult.errorOf(onArgNotFound(pack, indexNotFound))

    fun onNotArgEnoughResult(pack: CommandContext?, nArgs: Int): CommandResult =
        CommandResult.errorOf(onNotArgEnough(pack, nArgs))

    companion object {
        //	arg type
        const val PLAIN_TEXT: Int = 10
        const val FILE: Int = 11
        const val VISIBLE_PACKAGE: Int = 12
        const val CONTACTNUMBER: Int = 13
        const val TEXTLIST: Int = 14
        const val COMMAND: Int = 17
        const val PARAM: Int = 18
        const val BOOLEAN: Int = 19
        const val HIDDEN_PACKAGE: Int = 20
        const val COLOR: Int = 21
        const val CONFIG_FILE: Int = 22
        const val CONFIG_ENTRY: Int = 23
        const val INT: Int = 24
        const val DEFAULT_APP: Int = 25
        const val ALL_PACKAGES: Int = 26
        const val NO_SPACE_STRING: Int = 27
        const val APP_GROUP: Int = 28
        const val APP_INSIDE_GROUP: Int = 29
        const val LONG: Int = 30
        const val BOUND_REPLY_APP: Int = 31
        const val DATASTORE_PATH_TYPE: Int = 32
    }
}
