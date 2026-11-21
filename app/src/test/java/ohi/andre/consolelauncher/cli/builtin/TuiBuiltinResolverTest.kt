package ohi.andre.consolelauncher.cli.builtin

import android.content.Context
import org.robolectric.RuntimeEnvironment
import io.mockk.mockk
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandParser
import ohi.andre.consolelauncher.commands.main.raw.clear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TuiBuiltinResolverTest {
    private val app: Context = RuntimeEnvironment.getApplication()
    private val ctx = CommandContext(app, CommandGroup(listOf(clear())))
    private val resolver = TuiBuiltinResolver(ctx, CommandParser(mockk(relaxed = true), null))

    @Test
    fun builtinNameResolvesToThatBuiltin() {
        assertEquals("builtin:clear", resolver.resolve("clear")?.label)
        assertEquals("clear", ctx.lastCommand)
    }

    @Test
    fun otherInputFallsThrough() {
        assertNull(resolver.resolve("ls -l"))
        assertNull(resolver.resolve("./clear"))
    }
}
