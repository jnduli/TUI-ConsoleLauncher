package ohi.andre.consolelauncher.cli

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import org.robolectric.RuntimeEnvironment
import ohi.andre.consolelauncher.commands.CommandResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActivityStarterTest {
    private val app: Context = RuntimeEnvironment.getApplication()

    /** A context whose startActivity throws [e]. */
    private fun failingWith(e: Exception) = object : ContextWrapper(app) {
        override fun startActivity(intent: Intent) = throw e
    }

    @Test
    fun successfulLaunchIsEmpty() {
        val intent = Intent(Intent.ACTION_VIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(CommandResult.Empty, ActivityStarter.start(app, intent, "maps"))
    }

    @Test
    fun failuresBecomeShortErrorsInsteadOfThrowing() {
        val cases = mapOf(
            ActivityNotFoundException() to "maps: no app can open this",
            SecurityException() to "maps: not allowed to launch",
            IllegalStateException() to "maps: can't launch right now",
            RuntimeException() to "maps: couldn't launch",
        )
        for ((e, message) in cases) {
            val result = ActivityStarter.start(failingWith(e), Intent(), "maps")
            assertEquals(CommandResult.Error(message), result)
        }
    }
}
