package ohi.andre.consolelauncher.cli.launchable

import android.content.Context
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.cli.ActionKind
import ohi.andre.consolelauncher.cli.ActionResolver
import ohi.andre.consolelauncher.cli.CliAction
import ohi.andre.consolelauncher.cli.FailedAction
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.launchable.AliasLauncher
import ohi.andre.consolelauncher.managers.launchable.AppLauncher
import ohi.andre.consolelauncher.managers.launchable.Launchable
import ohi.andre.consolelauncher.managers.launchable.WebLauncher

/** Starts an app-list entry. [query] fills a [WebLauncher]'s `%s` placeholder when present. */
class LaunchAction(
    private val appsManager: AppsManager,
    private val context: Context,
    val launchable: Launchable,
    private val query: String? = null,
) : CliAction {
    override val kind: ActionKind = when (launchable) {
        is AppLauncher -> ActionKind.APP
        is WebLauncher -> ActionKind.WEB
        is AliasLauncher -> ActionKind.ALIAS
    }
    override val name: String = launchable.name()

    override fun run(): CommandResult =
        if (query != null && launchable is WebLauncher) launchable.launchWithQuery(context, query)
        else appsManager.performLaunch(launchable)
}

/** Matches input against shown app-list entries: `<label>`, or `<web label> <query>`. */
class LaunchableResolver(
    private val appsManager: AppsManager,
    private val context: Context,
) : ActionResolver {
    override fun resolve(input: String): CliAction? {
        appsManager.findLaunchableWithLabel(input)?.let { return LaunchAction(appsManager, context, it) }

        val spaceIdx = input.indexOf(' ')
        if (spaceIdx == -1) return null

        val web = appsManager.findLaunchableWithLabel(input.substring(0, spaceIdx)) as? WebLauncher ?: return null
        if (!web.url.contains("%s")) {
            // Claim the input anyway: falling through would hand it to the shell, whose
            // "not found" hides that the web entry exists but can't take a query.
            return FailedAction(ActionKind.WEB, web.name, context.getString(R.string.web_no_query_slot, web.name))
        }
        return LaunchAction(appsManager, context, web, input.substring(spaceIdx + 1).trim())
    }
}
