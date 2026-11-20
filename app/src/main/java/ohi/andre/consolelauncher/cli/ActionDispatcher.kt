package ohi.andre.consolelauncher.cli

import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.tuils.LogTuils
import ohi.andre.consolelauncher.tuils.OutputTuils
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.StringTuils

/**
 * The CLI input pipeline: every line typed into the terminal goes through here and leaves an
 * `input -> action -> result` trail in logcat under [TAG].
 *
 * Resolving runs on the caller's thread (resolvers fill the shared [CommandContext]); actions
 * always run on a background thread.
 *
 * @param resolvers builds the ordered resolver list from a `reenter` function that feeds a line
 *   back into the pipeline (used by aliases).
 */
class ActionDispatcher(
    private val ctx: CommandContext,
    resolvers: (reenter: (String) -> Unit) -> List<ActionResolver>,
) {
    private val resolvers: List<ActionResolver> = resolvers(::submit)

    /** A line of input (typed, or an alias expansion): resolves and runs it. */
    fun submit(line: String) = dispatch(StringTuils.removeUnncesarySpaces(line))

    /**
     * An action the UI already picked, such as a tapped suggestion. Runs it as-is instead of
     * resolving [line] again, which could pick a different target (a builtin with the same name,
     * or another app with the same label).
     */
    fun submit(action: CliAction, line: String) = execute(action, StringTuils.removeUnncesarySpaces(line))

    /** Resolves [input] with the first matching resolver and runs it. */
    private fun dispatch(input: String) {
        ctx.clear()
        val action = try {
            resolvers.firstNotNullOfOrNull { it.resolve(input) }
        } catch (e: Exception) {
            Log.e(TAG, "'$input' -> resolve failed", e)
            render(crashed(e))
            return
        }

        if (action == null) {
            Log.d(TAG, "'$input' -> no action")
            return
        }
        execute(action, input)
    }

    /**
     * Runs [action] on a background thread and logs its timing:
     * - `wait`: from this call until the thread starts.
     * - `run`: how long [CliAction.run] took. For launches that's only until startActivity
     *   returns (ActivityTaskManager logs `Displayed ... +Nms` when the screen is drawn); for
     *   shell commands it's only the hand-off to the shell.
     */
    private fun execute(action: CliAction, input: String) {
        Log.d(TAG, "'$input' -> ${action.label}")
        val queuedAt = SystemClock.elapsedRealtimeNanos()
        object : StoppableThread() {
            override fun run() {
                val startedAt = SystemClock.elapsedRealtimeNanos()
                val result = try {
                    action.run()
                } catch (e: Exception) {
                    // Launches report their own failures, so anything thrown here is a bug.
                    crashed(e)
                }
                val finishedAt = SystemClock.elapsedRealtimeNanos()
                Log.d(
                    TAG,
                    "${action.label} -> ${result.javaClass.simpleName}" +
                        "  wait=${millis(startedAt - queuedAt)}ms run=${millis(finishedAt - startedAt)}ms",
                )
                render(result)
            }
        }.start()
    }

    private fun millis(nanos: Long): Long = nanos / 1_000_000

    private fun crashed(e: Exception): CommandResult {
        LogTuils.log(e)
        return CommandResult.Error(LogTuils.getStackTrace(e))
    }

    private fun render(result: CommandResult) {
        when (result) {
            is CommandResult.Output -> OutputTuils.sendOutput(
                ctx.context,
                result.text,
                result.color,
                TerminalManager.CATEGORY_OUTPUT,
            )
            is CommandResult.Error -> OutputTuils.sendOutput(
                ctx.context,
                result.text,
                Color.RED,
                TerminalManager.CATEGORY_OUTPUT,
            )
            CommandResult.Empty -> {}
        }
    }

    companion object {
        const val TAG = "CliDispatch"
    }
}
