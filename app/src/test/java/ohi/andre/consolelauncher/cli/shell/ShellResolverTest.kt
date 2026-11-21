package ohi.andre.consolelauncher.cli.shell

import android.content.Context
import org.robolectric.RuntimeEnvironment
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.tuils.libsuperuser.Shell
import ohi.andre.consolelauncher.tuils.libsuperuser.ShellHolder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShellResolverTest {
    @get:Rule val tmp = TemporaryFolder()

    private val app: Context = RuntimeEnvironment.getApplication()
    private val ctx = CommandContext(app, CommandGroup(emptyList()))
    private val shell = mockk<Shell.Interactive>(relaxed = true)

    @Before
    fun setUp() {
        MainManager.interactive = shell
    }

    /** Runs [line] and returns the listener its directory check reports to. */
    private fun run(line: String): Shell.OnCommandResultListener {
        ShellResolver(ctx).resolve(line).run()
        val listener = slot<Shell.OnCommandResultListener>()
        verify { shell.addCommand(any<String>(), ShellAction.DIR_CODE, capture(listener)) }
        return listener.captured
    }

    @Test
    fun anyInputIsAShellCommandFollowedByADirectoryCheck() {
        run("ls -l")
        verifyOrder {
            shell.addCommand("ls -l")
            shell.addCommand(match<String> { ShellHolder.INTERNAL_MARKER in it }, ShellAction.DIR_CODE, any<Shell.OnCommandResultListener>())
        }
    }

    @Test
    fun directoryCheckUpdatesTheCurrentDirectory() {
        val dir = tmp.newFolder("music")
        run("cd music").onCommandResult(ShellAction.DIR_CODE, 0, mutableListOf(ShellHolder.INTERNAL_MARKER + dir.path))
        assertEquals(dir, ctx.currentDirectory)
    }

    @Test
    fun directoryCheckIgnoresPathsThatArentDirectories() {
        val before = ctx.currentDirectory
        val missing = File(tmp.root, "missing").path
        run("cd missing").onCommandResult(ShellAction.DIR_CODE, 1, mutableListOf(ShellHolder.INTERNAL_MARKER + missing))
        assertEquals(before, ctx.currentDirectory)
    }
}
