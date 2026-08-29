package ohi.andre.consolelauncher.commands

/** One parsed argument: what was found, how much of the input it consumed, and what's left. */
class ArgInfo(
    @JvmField var arg: Any?,
    @JvmField var residualString: String?,
    @JvmField var found: Boolean,
    @JvmField var n: Int
)
