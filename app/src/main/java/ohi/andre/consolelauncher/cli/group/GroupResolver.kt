package ohi.andre.consolelauncher.cli.group

import android.content.Context
import ohi.andre.consolelauncher.cli.ActionResolver
import ohi.andre.consolelauncher.cli.CliAction
import ohi.andre.consolelauncher.cli.launchable.LaunchAction
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.tuils.StringTuils

/**
 * App-group shorthands. Groups have no action kind of their own; they resolve to other actions:
 * - `<group> <member>` launches that member (a [LaunchAction]). An unknown member falls through
 *   to the next resolver.
 * - `<group>` alone is shorthand for `apps -lsgp <group>`, resolved by [builtins].
 */
class GroupResolver(
    private val appsManager: AppsManager,
    private val context: Context,
    private val builtins: ActionResolver,
) : ActionResolver {
    override fun resolve(input: String): CliAction? {
        val spaceIdx = input.indexOf(' ')
        val name = if (spaceIdx == -1) input else input.substring(0, spaceIdx)
        val rest = if (spaceIdx == -1) null else input.substring(spaceIdx + 1)

        val group = appsManager.groups.find { it.name() == name } ?: return null
        if (rest == null) return builtins.resolve("apps -lsgp ${group.name}")

        val label = StringTuils.removeSpaces(rest)
        val member = group.apps.find { it.names().contains(label) } ?: return null
        return LaunchAction(appsManager, context, member)
    }
}
