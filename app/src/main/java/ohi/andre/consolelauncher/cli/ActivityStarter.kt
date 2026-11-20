package ohi.andre.consolelauncher.cli

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandResult

/**
 * The one place the CLI starts other activities. Every launch is logged under [TAG]. Launches
 * never throw: any failure becomes a [CommandResult.Error] with a short message, and the full
 * exception goes to logcat.
 */
object ActivityStarter {
    const val TAG = "ActivityStarter"

    fun start(context: Context, intent: Intent, label: String): CommandResult = launching(context, label) {
        Log.i(TAG, "start $label: $intent")
        context.startActivity(intent)
    }

    /**
     * Starts an app shortcut: the per-app actions a home screen shows on long-press, such as a
     * messaging app's "chat with X" or a maps app's "navigate home". Used by the `shortcut` builtin.
     *
     * Shortcuts aren't intents we can pass to [start]. The publishing app keeps the intent, and
     * Android only lets the default launcher start it, through [LauncherApps.startShortcut]. That
     * throws [IllegalStateException] when T-UI isn't the default launcher or the user is locked,
     * which is reported as "can't launch right now".
     */
    fun startShortcut(context: Context, packageName: String, shortcutId: String, label: String): CommandResult =
        launching(context, label) {
            Log.i(TAG, "start shortcut $label: $packageName/$shortcutId")
            val apps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            apps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
        }

    /** Turns a launch failure into a short user-facing error; the full exception goes to logcat. */
    private fun describe(context: Context, label: String, e: Exception): CommandResult.Error {
        val res = when (e) {
            is ActivityNotFoundException -> R.string.launch_no_activity
            is SecurityException -> R.string.launch_not_allowed
            // LauncherApps.startShortcut throws this when T-UI isn't the default launcher or
            // the user is locked.
            is IllegalStateException -> R.string.launch_unavailable
            else -> R.string.launch_failed
        }
        Log.w(TAG, "failed to start $label", e)
        return CommandResult.Error(context.getString(res, label))
    }

    private inline fun launching(context: Context, label: String, block: () -> Unit): CommandResult =
        try {
            block()
            CommandResult.Empty
        } catch (e: Exception) {
            describe(context, label, e)
        }
}
