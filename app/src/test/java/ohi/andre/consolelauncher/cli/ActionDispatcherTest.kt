package ohi.andre.consolelauncher.cli

import android.content.Context
import org.robolectric.RuntimeEnvironment
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandGroup
import ohi.andre.consolelauncher.commands.CommandResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ActionDispatcherTest {
    private val app: Context = RuntimeEnvironment.getApplication()
    private val ctx = CommandContext(app, CommandGroup(emptyList()))

    /** Names of the actions that ran, in order. Actions run on background threads. */
    private val ran: MutableList<String> = Collections.synchronizedList(mutableListOf())
    private lateinit var done: CountDownLatch

    private fun action(name: String, body: () -> Unit = {}) = object : CliAction {
        override val kind = ActionKind.SHELL
        override val name = name
        override fun run(): CommandResult {
            body()
            ran += name
            done.countDown()
            return CommandResult.Empty
        }
    }

    /** Claims exactly [input] with an action named [name]. */
    private fun claims(input: String, name: String, body: () -> Unit = {}) =
        ActionResolver { if (it == input) action(name, body) else null }

    private fun await(count: Int) {
        assertTrue("actions didn't run: $ran", done.await(5, TimeUnit.SECONDS))
        assertEquals(count, ran.size)
    }

    @Test
    fun firstResolverThatClaimsTheInputWins() {
        done = CountDownLatch(1)
        val dispatcher = ActionDispatcher(ctx) {
            listOf(claims("other", "unrelated"), claims("ls", "first"), claims("ls", "second"))
        }
        dispatcher.submit("ls")
        await(1)
        assertEquals(listOf("first"), ran)
    }

    @Test
    fun repeatedSpacesAreCollapsedBeforeResolving() {
        done = CountDownLatch(1)
        val dispatcher = ActionDispatcher(ctx) { listOf(claims("ls -l", "ls")) }
        dispatcher.submit("ls   -l")
        await(1)
    }

    @Test
    fun reenterRunsTheExpandedLineThroughThePipeline() {
        done = CountDownLatch(2)
        val dispatcher = ActionDispatcher(ctx) { reenter ->
            listOf(claims("ll", "alias") { reenter("ls -l") }, claims("ls -l", "shell"))
        }
        dispatcher.submit("ll")
        await(2)
        assertEquals(listOf("alias", "shell"), ran)
    }

    @Test
    fun preResolvedActionRunsWithoutResolving() {
        done = CountDownLatch(1)
        val dispatcher = ActionDispatcher(ctx) { listOf(claims("chrome", "resolved")) }
        dispatcher.submit(action("tapped"), "chrome")
        await(1)
        assertEquals(listOf("tapped"), ran)
    }
}
