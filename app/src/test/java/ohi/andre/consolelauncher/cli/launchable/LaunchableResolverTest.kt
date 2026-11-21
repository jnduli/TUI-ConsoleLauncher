package ohi.andre.consolelauncher.cli.launchable

import android.content.Context
import org.robolectric.RuntimeEnvironment
import io.mockk.every
import io.mockk.mockk
import ohi.andre.consolelauncher.cli.ActionKind
import ohi.andre.consolelauncher.cli.FailedAction
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.launchable.AppLauncher
import ohi.andre.consolelauncher.managers.launchable.WebLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LaunchableResolverTest {
    private val app: Context = RuntimeEnvironment.getApplication()

    private val entries = mapOf(
        "chrome" to AppLauncher("com.android.chrome", "Main", "chrome"),
        "google" to WebLauncher("google", "https://google.com/search?q=%s"),
        "home" to WebLauncher("home", "https://example.com"),
    )
    private val apps = mockk<AppsManager> {
        every { findLaunchableWithLabel(any()) } answers { entries[firstArg()] }
    }
    private val resolver = LaunchableResolver(apps, app)

    @Test
    fun labelLaunchesThatEntry() {
        val action = resolver.resolve("chrome") as LaunchAction
        assertEquals("app:chrome", action.label)
        assertEquals(ActionKind.WEB, (resolver.resolve("google") as LaunchAction).kind)
    }

    @Test
    fun webLabelWithQuerySearches() {
        val action = resolver.resolve("google cats") as LaunchAction
        assertEquals("web:google", action.label)
    }

    @Test
    fun queryOnWebEntryWithoutPlaceholderIsAnError() {
        val action = resolver.resolve("home cats") as FailedAction
        assertEquals("web:home", action.label)
        val result = action.run() as CommandResult.Error
        assertTrue(result.text.toString(), result.text.startsWith("home: url has no %s"))
    }

    @Test
    fun unknownInputAndQueriesOnAppsFallThrough() {
        assertNull(resolver.resolve("nothing"))
        assertNull(resolver.resolve("chrome cats"))
    }
}
