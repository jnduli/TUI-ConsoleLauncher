package ohi.andre.consolelauncher.cli.shell

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.cli.ActionKind
import ohi.andre.consolelauncher.cli.ActionResolver
import ohi.andre.consolelauncher.cli.CliAction
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell.OnCommandResultListener
import ohi.andre.consolelauncher.tuils.libsuperuser.ShellHolder
import java.io.File

/** Sends a line to the interactive shell. Output arrives asynchronously via the shell's own callbacks. */
class ShellAction(
    private val line: String,
    private val ctx: CommandContext,
    private val dirListener: OnCommandResultListener,
) : CliAction {
    override val kind = ActionKind.SHELL
    override val name: String = line.substringBefore(' ')

    override fun run(): CommandResult {
        val interactive = MainManager.interactive
        if (line.trim().equals("su", ignoreCase = true)) {
            if (Shell.SU.available()) broadcast(UIManager.ACTION_ROOT)
            interactive.addCommand("su")
            return CommandResult.Empty
        }
        interactive.addCommand(line)
        // The shell's working directory lives in its own process, and T-UI keeps a copy in
        // ctx.currentDirectory (prompt, file args and suggestions). Ask for it after every
        // command rather than guessing which lines change it (`cd`, bare `cd`, `pushd`, scripts).
        // Commands run in order, so this reports the directory [line] left behind.
        interactive.addCommand("echo \"${ShellHolder.INTERNAL_MARKER}\$PWD\"", DIR_CODE, dirListener)
        return CommandResult.Empty
    }

    private fun broadcast(action: String) {
        LocalBroadcastManager.getInstance(ctx.context.applicationContext).sendBroadcast(Intent(action))
    }

    companion object {
        const val DIR_CODE = 10
    }
}

/** The catch-all: anything no other resolver claimed is a shell command. */
class ShellResolver(private val ctx: CommandContext) : ActionResolver {

    /** Copies the shell's directory into [ctx] and refreshes the prompt when it changed. */
    private val dirListener = OnCommandResultListener { commandCode, _, output ->
        if (commandCode != ShellAction.DIR_CODE) return@OnCommandResultListener
        val path = output.lastOrNull()?.removePrefix(ShellHolder.INTERNAL_MARKER) ?: return@OnCommandResultListener
        val dir = File(path)
        if (!dir.isDirectory || dir == ctx.currentDirectory) return@OnCommandResultListener
        ctx.currentDirectory = dir
        LocalBroadcastManager.getInstance(ctx.context.applicationContext)
            .sendBroadcast(Intent(UIManager.ACTION_UPDATE_HINT))
    }

    override fun resolve(input: String): CliAction = ShellAction(input, ctx, dirListener)
}
