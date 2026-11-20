package ohi.andre.consolelauncher.cli

import ohi.andre.consolelauncher.commands.CommandResult

/** Every kind of thing the CLI can run. [tag] prefixes the action's name in logs. */
enum class ActionKind(val tag: String) {
    APP("app"),
    WEB("web"),
    SHORTCUT("shortcut"),
    ALIAS("alias"),
    TUI_BUILTIN("builtin"),
    SHELL("shell"),
}

/** Anything the CLI can run: what a line of input resolves to before it's executed. */
interface CliAction {
    val kind: ActionKind

    /** What the user knows this action as, e.g. `gmail`, `search`; used in user-facing messages. */
    val name: String

    /** `kind:name` identifier used in logs, e.g. `web:gmail`, `builtin:search`, `shell:ls`. */
    val label: String get() = "${kind.tag}:$name"

    fun run(): CommandResult
}

/** Decides whether a line of input is meant for one kind of [CliAction]. */
fun interface ActionResolver {
    /** The action [input] resolves to, or null to let the next resolver try. */
    fun resolve(input: String): CliAction?
}
