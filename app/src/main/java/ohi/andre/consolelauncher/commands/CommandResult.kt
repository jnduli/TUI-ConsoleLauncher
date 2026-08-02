package ohi.andre.consolelauncher.commands

/**
 * Richer alternative to the legacy `String?` returned by [CommandAbstraction.exec] and friends:
 * distinguishes "produced output", "produced nothing" and "failed" as types instead of
 * overloading null/non-null. Existing Java commands are unaffected -- they keep returning
 * `String?`, which [of]/[errorOf] adapt into this type via default bridge methods.
 */
sealed class CommandResult {
    data class Output(val text: CharSequence, val color: Int = Integer.MAX_VALUE) : CommandResult()
    object Empty : CommandResult()
    data class Error(val text: CharSequence) : CommandResult()

    companion object {
        @JvmStatic
        fun of(text: CharSequence?): CommandResult =
            if (text.isNullOrEmpty()) Empty else Output(text)

        @JvmStatic
        fun errorOf(text: CharSequence?): CommandResult =
            if (text.isNullOrEmpty()) Empty else Error(text)

        @JvmStatic
        fun toLegacyString(result: CommandResult): String? = when (result) {
            is Output -> result.text.toString()
            is Error -> result.text.toString()
            Empty -> null
        }
    }
}
