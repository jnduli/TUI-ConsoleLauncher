package ohi.andre.consolelauncher.commands.main.raw

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.ALL_PACKAGES
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.APP_GROUP
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.APP_INSIDE_GROUP
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.COLOR
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.DEFAULT_APP
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.HIDDEN_PACKAGE
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.INT
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.NO_SPACE_STRING
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.PLAIN_TEXT
import ohi.andre.consolelauncher.commands.CommandAbstraction.Companion.VISIBLE_PACKAGE
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.main.specific.ParamCommand
import ohi.andre.consolelauncher.managers.launchable.AppLauncher
import ohi.andre.consolelauncher.managers.AppsManager
import ohi.andre.consolelauncher.managers.launchable.Launchable
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.SystemUtils

class apps(private val appsManager: AppsManager) : ParamCommand() {

    // not a static enum: entries need access to the outer instance's appsManager
    private abstract class Param(private val paramName: String) : ohi.andre.consolelauncher.commands.main.Param {
        override fun label(): String = Tuils.MINUS + paramName

        override fun onNotArgEnough(pack: CommandContext, n: Int): String? {
            return pack.context.getString(R.string.help_apps)
        }

        override fun onArgNotFound(pack: CommandContext, index: Int): String {
            return pack.context.getString(R.string.output_appnotfound)
        }
    }

    private val ls = object : Param("ls") {
        override fun args(): IntArray = intArrayOf(PLAIN_TEXT)

        override fun exec(pack: CommandContext): String? {
            return appsManager.printApps(AppsManager.SHOWN_APPS, pack.getString())
        }

        override fun onNotArgEnough(pack: CommandContext, n: Int): String? {
            return appsManager.printApps(AppsManager.SHOWN_APPS)
        }
    }

    private val lsh = object : Param("lsh") {
        override fun args(): IntArray = intArrayOf(PLAIN_TEXT)

        override fun exec(pack: CommandContext): String? {
            return appsManager.printApps(AppsManager.HIDDEN_APPS, pack.getString())
        }

        override fun onNotArgEnough(pack: CommandContext, n: Int): String? {
            return appsManager.printApps(AppsManager.HIDDEN_APPS)
        }
    }

    private val show = object : Param("show") {
        override fun args(): IntArray = intArrayOf(HIDDEN_PACKAGE)

        override fun exec(pack: CommandContext): String? {
            val i = pack.getLaunchable()
            appsManager.showActivity(i)
            return null
        }
    }

    private val hide = object : Param("hide") {
        override fun args(): IntArray = intArrayOf(VISIBLE_PACKAGE)

        override fun exec(pack: CommandContext): String? {
            val i = pack.getLaunchable()
            appsManager.hideActivity(i)
            return null
        }
    }

    private val l = object : Param("l") {
        override fun args(): IntArray = intArrayOf(VISIBLE_PACKAGE)

        override fun exec(pack: CommandContext): String {
            try {
                val i = pack.getLaunchable()

                val info = pack.context.getPackageManager().getPackageInfo(
                    i.componentName!!.getPackageName(),
                    PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
                )
                return (i as AppLauncher).format(info)
            } catch (e: PackageManager.NameNotFoundException) {
                return e.toString()
            }
        }
    }

    private val ps = object : Param("ps") {
        override fun args(): IntArray = intArrayOf(VISIBLE_PACKAGE)

        override fun exec(pack: CommandContext): String? {
            openPlaystore(pack.context, pack.getLaunchable().componentName?.packageName)
            return null
        }
    }

    private val default_app = object : Param("default_app") {
        override fun args(): IntArray = intArrayOf(INT, DEFAULT_APP)

        override fun exec(pack: CommandContext): String? {
            val index = pack.getInt()

            val o = pack.get()

            val marker: String?
            if (o is Launchable) {
                val i = o
                marker = i.storageKey()
            } else {
                marker = o as String?
            }

            try {
                val save: XMLPrefsSave = Apps.valueOf("default_app_n" + index)
                save.parent().write(pack.context, save, marker)
                return null
            } catch (e: Exception) {
                return pack.context.getString(R.string.invalid_integer)
            }
        }

        override fun onArgNotFound(pack: CommandContext, index: Int): String {
            val res: Int
            if (index == 1) res = R.string.invalid_integer
            else res = R.string.output_appnotfound

            return pack.context.getString(res)
        }
    }

    private val st = object : Param("st") {
        override fun args(): IntArray = intArrayOf(VISIBLE_PACKAGE)

        override fun exec(pack: CommandContext): String? {
            openSettings(pack.context, pack.getLaunchable().componentName?.packageName)
            return null
        }
    }

    private val frc = object : Param("frc") {
        override fun args(): IntArray = intArrayOf(ALL_PACKAGES)

        override fun exec(pack: CommandContext): String? {
            val intent = appsManager.getIntent(pack.getLaunchable())
            pack.context.startActivity(intent)

            return null
        }
    }

    private val reset = object : Param("reset") {
        override fun args(): IntArray = intArrayOf(VISIBLE_PACKAGE)

        override fun exec(pack: CommandContext): String? {
            val app = pack.getLaunchable()
            app.launchTimes = 0
            appsManager.writeLaunchTimes(app)

            return null
        }
    }

    private val mkgp = object : Param("mkgp") {
        override fun args(): IntArray = intArrayOf(NO_SPACE_STRING)

        override fun exec(pack: CommandContext): String? {
            val name = pack.getString()
            return appsManager.createGroup(name)
        }
    }

    private val rmgp = object : Param("rmgp") {
        override fun args(): IntArray = intArrayOf(APP_GROUP)

        override fun exec(pack: CommandContext): String? {
            val name = pack.getString()
            return appsManager.removeGroup(name)
        }
    }

    private val gp_bg_color = object : Param("gp_bg_color") {
        override fun args(): IntArray = intArrayOf(APP_GROUP, COLOR)

        override fun exec(pack: CommandContext): String? {
            val name = pack.getString()
            val color = pack.getString()
            return appsManager.groupBgColor(name, color)
        }

        override fun onNotArgEnough(pack: CommandContext, n: Int): String? {
            if (n == 2) {
                val name = pack.getString()
                return appsManager.groupBgColor(name, Tuils.EMPTYSTRING)
            }
            return super.onNotArgEnough(pack, n)
        }

        override fun onArgNotFound(pack: CommandContext, index: Int): String {
            return pack.context.getString(R.string.output_invalidcolor)
        }
    }

    private val gp_fore_color = object : Param("gp_fore_color") {
        override fun args(): IntArray = intArrayOf(APP_GROUP, COLOR)

        override fun exec(pack: CommandContext): String? {
            val name = pack.getString()
            val color = pack.getString()
            return appsManager.groupForeColor(name, color)
        }

        override fun onNotArgEnough(pack: CommandContext, n: Int): String? {
            if (n == 2) {
                val name = pack.getString()
                return appsManager.groupForeColor(name, Tuils.EMPTYSTRING)
            }
            return super.onNotArgEnough(pack, n)
        }

        override fun onArgNotFound(pack: CommandContext, index: Int): String {
            return pack.context.getString(R.string.output_invalidcolor)
        }
    }

    private val lsgp = object : Param("lsgp") {
        override fun args(): IntArray = intArrayOf(APP_GROUP)

        override fun exec(pack: CommandContext): String? {
            val name = pack.getString()
            return appsManager.listGroup(name)
        }

        override fun onNotArgEnough(pack: CommandContext, n: Int): String? {
            return appsManager.listGroups()
        }
    }

    private val addtogp = object : Param("addtogp") {
        override fun args(): IntArray = intArrayOf(APP_GROUP, VISIBLE_PACKAGE)

        override fun exec(pack: CommandContext): String? {
            val name = pack.getString()
            val app = pack.getLaunchable()
            return appsManager.addAppToGroup(name, app)
        }
    }

    private val rmfromgp = object : Param("rmfromgp") {
        override fun args(): IntArray = intArrayOf(APP_GROUP, APP_INSIDE_GROUP)

        override fun exec(pack: CommandContext): String? {
            val name = pack.getString()
            val app = pack.getLaunchable()
            return appsManager.removeAppFromGroup(name, app)
        }
    }

    private val addweb = object : Param("addweb") {
        override fun args(): IntArray = intArrayOf(PLAIN_TEXT, PLAIN_TEXT)

        override fun exec(pack: CommandContext): String? {
            val name = pack.getString()
            val url = pack.getString()
            val success = appsManager.addWebApp(name, url)
            return if (success) "Web app '$name' saved" else "Error saving web app"
        }
    }

    private val listweb = object : Param("listweb") {
        override fun args(): IntArray = IntArray(0)

        override fun exec(pack: CommandContext): String? {
            return appsManager.listWebApps()
        }
    }

    private val tutorial = object : Param("tutorial") {
        override fun args(): IntArray = IntArray(0)

        override fun exec(pack: CommandContext): String? {
            pack.context.startActivity(SystemUtils.webPage("https://github.com/Andre1299/TUI-ConsoleLauncher/wiki/Apps"))
            return null
        }
    }

    private fun values(): Array<Param> = arrayOf(
        ls, lsh, show, hide, l, ps, default_app, st, frc, reset, mkgp, rmgp,
        gp_bg_color, gp_fore_color, lsgp, addtogp, rmfromgp, addweb, listweb, tutorial
    )

    override fun paramForString(
        pack: CommandContext,
        param: String
    ): ohi.andre.consolelauncher.commands.main.Param? {
        return ohi.andre.consolelauncher.commands.main.Param.find(values(), param)
    }

    override fun doThings(pack: CommandContext): String? {
        return null
    }

    override fun helpRes(): Int {
        return R.string.help_apps
    }

    override fun priority(): Int {
        return 4
    }

    override fun params(): Array<String> {
        return ohi.andre.consolelauncher.commands.main.Param.labels(values())
    }

    companion object {
        private fun openSettings(context: Context, packageName: String?) {
            SystemUtils.openSettingsPage(context, packageName)
        }

        private fun openPlaystore(context: Context, packageName: String?) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=" + packageName)
                    )
                )
            } catch (e: Exception) {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)
                    )
                )
            }
        }
    }
}
