package ohi.andre.consolelauncher.managers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.ShortcutInfo
import android.graphics.Color
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.os.Process
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import it.andreuzzi.comparestring2.StringableObject
import kotlinx.parcelize.Parcelize
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.WebActivity
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXParseException
import java.io.File
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlSerialName


private val APP_TAG: String = "AppsManager"

enum class LauncherType {
    APPLICATION, WEB
}

enum class Visibility {
    SHOWN, HIDDEN
}

@Serializable
sealed interface Launchable: Comparable<Launchable> {
    var launchTimes: Int
    var componentName: ComponentName?

    fun launch(context: Context) {
        launchTimes++
        val intent = toIntent(context)
        context.startActivity(intent)
    }

    fun toIntent(context: Context): Intent
    fun label(): String
    fun names(): Set<String>
    fun name(): String
    fun write(): String

    override fun compareTo(other: Launchable): Int {
        return name().lowercase(Locale.getDefault()).compareTo(other.name().lowercase(Locale.getDefault()))
    }

    companion object {
        fun listFromXml(xml: String): List<Launchable> {
            return try {
                XML.decodeFromString<LaunchableList>(xml).launchables
            } catch (e: Exception) {
                Log.e(APP_TAG, "Error decoding launchables list", e)
                emptyList()
            }
        }

        fun listToXml(launchables: List<Launchable>): String {
            return XML.encodeToString(LaunchableList(launchables))
        }
    }
}

@Serializable
@XmlSerialName("launchables", "", "")
data class LaunchableList(val launchables: List<Launchable>)

@Parcelize
@Serializable
@XmlSerialName("web", "", "")
data class WebLauncher(val name: String, val url: String, override var launchTimes: Int = 0) : Launchable, Parcelable {

    override var componentName: ComponentName? = ComponentName("ohi.andre.consolelauncher", "ohi.andre.consolelauncher.WebActivity")

    override fun toIntent(context: Context): Intent {
        val intent = Intent(context, WebActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply {
                putExtra(WebActivity.URL_EXTRA, url)
        }
        return intent
    }

    override fun label() : String {
        return name
    }

    override fun names() : Set<String>{
        return setOf(name)
    }

    override fun name(): String {
        return name
    }

    override fun write(): String = name
}

@Parcelize
@Serializable
@XmlSerialName("app", "", "")
data class AppLauncher(val packageName: String, val activityName: String, val label: String, var shortcut: String = "", override var launchTimes: Int = 0): Launchable, Parcelable {

    override var componentName: ComponentName = ComponentName(packageName, activityName)

    override fun toIntent(context: Context): Intent {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(componentName).setFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val msg = "--> $packageName:$activityName:$label\n"
        val text = SpannableString(msg)
        val outputColor = XMLPrefsManager.getColor(Theme.output_color)
        text.setSpan(
            ForegroundColorSpan(outputColor),
            0,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        val s = TimeManager.instance.replace(text)
        Tuils.sendOutput(context, s, TerminalManager.CATEGORY_OUTPUT)
        return intent
    }

    override fun name(): String {
        return label
    }

    override fun names(): Set<String> {
        return setOf(label, packageName)
    }

    override fun label() : String {
        return label
    }

    override fun write(): String = "$packageName-$activityName"

}

class LauncherManager(val context: Context, val mgr: PackageManager) {

    // TODO: handle hidden here too
    fun findLauncher(label: String, visibility: Visibility): Launchable? {
        val launchables = createLaunchablesList(context, mgr)
        val stripLabel = label.filter { !it.isWhitespace() }
        launchables.forEach {
            val appLabels = it.names()
            if (appLabels.contains(stripLabel)) {
                return it
            }
        }
        return null
    }

    fun hide(launchable: Launchable) {
        val root = Tuils.getFolder(context)
        val file = File(root, "apps.xml")
        
        val name = when(launchable) {
            is AppLauncher -> launchable.packageName + "-" + launchable.activityName
            is WebLauncher -> launchable.name
        }
        
        XMLPrefsManager.set(file, name, arrayOf("show"), arrayOf("false"))
    }

    fun saveLaunchables(launchables: List<Launchable>, fileName: String = "launchables.xml") {
        try {
            val file = File(Tuils.getFolder(context), fileName)
            val xmlString = Launchable.listToXml(launchables)
            file.writeText(xmlString)
        } catch (e: Exception) {
            Log.e(APP_TAG, "Error saving launchables", e)
        }
    }

    fun loadLaunchables(fileName: String = "launchables.xml"): List<Launchable> {
        return try {
            val file = File(Tuils.getFolder(context), fileName)
            if (file.exists()) {
                val xmlString = file.readText()
                Launchable.listFromXml(xmlString)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(APP_TAG, "Error loading launchables", e)
            emptyList()
        }
    }


    fun createLaunchablesList(context: Context, mgr: PackageManager): List<Launchable> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val main = mgr.queryIntentActivities(intent, 0 )
        val apps: MutableList<Launchable> = ArrayList<Launchable>()

        for (app in main) {
            val activityInfo = app.activityInfo
            val appLauncher = AppLauncher(activityInfo.packageName, activityInfo.name,
                app.loadLabel(mgr) as String, "")
            // Note: For SIM TOolkit, the name 'SIM Toolkit' is defined as one of the shortcuts so not the actual app name found by
            // resovleinfo.loadLabel(mgr), so ensure T-UI is the default for this to work
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                try {
                    val launcherApps =
                        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val query = ShortcutQuery()
                    query.setQueryFlags(ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC)
                    query.setPackage(appLauncher.packageName)
                    val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                    if (shortcuts != null && shortcuts.isNotEmpty()) {
                        appLauncher.shortcut = shortcuts[0].id
                    }
                } catch (e: SecurityException) {
                    Log.e(APP_TAG, e.toString())
                } catch (e: Throwable) {
                    // t-ui is not the default launcher
                    Log.e(APP_TAG, e.toString())
                }
            }
            apps.add(appLauncher)
        }
        // TODO: jnduli fix this with generic implementation
        // val webApps = loadWebApps()
        // apps.addAll(webApps)
        return apps
    }
}


open class LaunchInfo : Parcelable, StringableObject, Comparable<LaunchInfo?> {
    @JvmField
    var componentName: ComponentName?
    @JvmField
    var publicLabel: String? = null
    @JvmField
    var unspacedLowercaseLabel: String? = null
    var lowercaseLabel: String? = null
    @JvmField
    var launchedTimes: Int = 0
    @JvmField
    var launcherType: LauncherType = LauncherType.APPLICATION
    @JvmField
    var shortcuts: MutableList<ShortcutInfo?>? = null
    @JvmField
    var activityName: String? = null
    @JvmField
    var webUrl: String? = null

    constructor(packageName: String, activityName: String, label: String, launcherType: LauncherType) {
        this.componentName = ComponentName(packageName, activityName)
        this.activityName = activityName
        this.launcherType = launcherType
        setLabel(label)
    }

    constructor(packageName: String, activityName: String, label: String, launcherType: LauncherType, webUrl: String?) {
        this.componentName = ComponentName(packageName, activityName)
        this.activityName = activityName
        this.launcherType = launcherType
        this.webUrl = webUrl
        setLabel(label)
    }

    protected constructor(`in`: Parcel) {
        componentName =
            `in`.readParcelable<ComponentName?>(ComponentName::class.java.getClassLoader())
        setLabel(`in`.readString()!!)
        launchedTimes = `in`.readInt()
        webUrl = `in`.readString()
    }

    fun setLabel(s: String) {
        this.publicLabel = s
        this.lowercaseLabel = s.lowercase(Locale.getDefault())
        this.unspacedLowercaseLabel = Tuils.removeSpaces(lowercaseLabel)
    }

    fun isInside(apps: String): Boolean {
        val split: Array<String> =
            apps.split(AppsManager.APPS_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (s in split) {
            if (`is`(s)) return true
        }
        return false
    }

    fun `is`(app: String): Boolean {
        val split2 = app.split(COMPONENT_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()

        if (split2.size == 1) {
            if (componentName!!.getPackageName() == split2[0]) return true
        } else {
            if (componentName!!.getPackageName() == split2[0] && componentName!!.getClassName() == split2[1]) return true
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }

        if (other is LaunchInfo) {
            val i = other
            try {
                if (this.componentName != i.componentName) return false
                if (launcherType == LauncherType.WEB || i.launcherType == LauncherType.WEB) {
                    return webUrl == i.webUrl
                }
                return true
            } catch (e: Exception) {
                return false
            }
        } else if (other is ComponentName) {
            return this.componentName == other
        } else if (other is String) {
            return `is`(other) || this.componentName!!.getClassName() == other
        }
        return false
    }

    override fun toString(): String {
        val base = componentName!!.getPackageName() + " - " + componentName!!.getClassName() + " --> " + publicLabel + ", n=" + launchedTimes
        return if (webUrl != null) "$base, url=$webUrl" else base
    }

    override fun getLowercaseString(): String? {
        return lowercaseLabel
    }

    override fun getString(): String? {
        return publicLabel
    }

    fun write(): String {
        return this.componentName!!.getPackageName() + COMPONENT_SEPARATOR + this.componentName!!.getClassName()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(componentName, flags)
        dest.writeString(publicLabel)
        dest.writeInt(launchedTimes)
        dest.writeString(webUrl)
    }

    fun setShortcuts(s: MutableList<ShortcutInfo?>?) {
        this.shortcuts = s
    }

    override fun compareTo(other: LaunchInfo?): Int {
        if (other == null) {
            return 0
        }
        return other.launchedTimes - launchedTimes
    }

    companion object {
        private const val COMPONENT_SEPARATOR = "-"

        @JvmField
        val CREATOR: Creator<LaunchInfo?> = object : Creator<LaunchInfo?> {
            override fun createFromParcel(`in`: Parcel): LaunchInfo {
                return LaunchInfo(`in`)
            }

            override fun newArray(size: Int): Array<LaunchInfo?> {
                return arrayOfNulls<LaunchInfo>(size)
            }
        }

        fun componentInfo(app: String): ComponentName? {
            val split2 = app.split(COMPONENT_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()

            if (split2.size == 1) {
                return null
            } else {
                return ComponentName(split2[0], split2[1])
            }
        }
    }
}

fun LaunchInfo.toLaunchable(): Launchable? {
    return when (launcherType) {
        LauncherType.APPLICATION -> {
            if (componentName != null && activityName != null && publicLabel != null) {
                AppLauncher(componentName!!.packageName, activityName!!, publicLabel!!)
            } else null
        }
        LauncherType.WEB -> {
            if (publicLabel != null && webUrl != null) {
                WebLauncher(publicLabel!!, webUrl!!)
            } else null
        }
    }
}


object AppUtils {
    fun findLaunchableWithComponent(appList: MutableList<Launchable>, name: ComponentName?): Launchable? {
        if (name == null) return null
        return appList.find { it.componentName == name }
    }

    fun findLaunchInfoWithComponent(appList: MutableList<LaunchInfo>, name: ComponentName?): LaunchInfo? {
        if (name == null) return null
        for (i in appList) {
            if (i == name) return i
        }
        return null
    }

    fun findLaunchInfoWithLabel(appList: MutableList<out LaunchInfo>, label: String): LaunchInfo? {
        var label = label
        label = Tuils.removeSpaces(label)
        for (i in appList) {
            if (i.unspacedLowercaseLabel.equals(label, ignoreCase = true )) return i
        }
        return null
    }

    fun findLaunchInfosWithPackage(packageName: String?, infos: MutableList<LaunchInfo>): MutableList<LaunchInfo?> {
        val result: MutableList<LaunchInfo?> = ArrayList<LaunchInfo?>()
        for (info in infos) {
            if (info.componentName!!.getPackageName() == packageName) result.add(info)
        }
        return result
    }

    fun checkEquality(list: MutableList<LaunchInfo>) {
        for (info in list) {
            if (info.publicLabel == null) {
                continue
            }
            for (count in list.indices) {
                val info2: LaunchInfo = list[count]
                if (info2.publicLabel == null) {
                    continue
                }
                if (info === info2) {
                    continue
                }

                if (info.unspacedLowercaseLabel == info2.unspacedLowercaseLabel) {
                    // there are two activities in the same app loadlabel gives the same result
                    if (info.componentName!!.getPackageName() == info2.componentName!!.getPackageName()) {
                        info.setLabel(
                            insertActivityName(
                                info.publicLabel,
                                info.componentName!!.getClassName()
                            )
                        )
                        info2.setLabel(
                            insertActivityName(
                                info2.publicLabel,
                                info2.componentName!!.getClassName()
                            )
                        )
                    } else {
                        info2.setLabel(
                            getNewLabel(
                                info2.publicLabel,
                                info2.componentName!!.getPackageName()
                            )!!
                        )
                    }
                }
            }
        }
    }

    var activityPattern: Pattern =
        Pattern.compile("activity", Pattern.CASE_INSENSITIVE or Pattern.LITERAL)

    fun insertActivityName(oldLabel: String?, activityName: String): String {
        var name: String?

        val lastDot = activityName.lastIndexOf(".")
        if (lastDot == -1) {
            name = activityName
        } else {
            name = activityName.substring(lastDot + 1)
        }

        name = activityPattern.matcher(name).replaceAll(Tuils.EMPTYSTRING)
        name = name.substring(0, 1).uppercase(Locale.getDefault()) + name.substring(1)
        return oldLabel + Tuils.SPACE + "-" + Tuils.SPACE + name
    }

    fun getNewLabel(oldLabel: String?, packageName: String): String? {
        try {
            var firstDot = packageName.indexOf(Tuils.DOT)
            if (firstDot == -1) {
//                    no dots in package name (nearly impossible)
                return packageName
            }
            firstDot++

            val secondDot = packageName.substring(firstDot).indexOf(Tuils.DOT)
            var prefix: String?
            if (secondDot == -1) {
//                    only one dot, so two words. The first is most likely to be the company name
//                    facebook.messenger
//                    is better than
//                    messenger.facebook
                prefix = packageName.substring(0, firstDot - 1)
                prefix =
                    prefix.substring(0, 1).uppercase(Locale.getDefault()) + prefix.substring(1)
                        .lowercase(
                            Locale.getDefault()
                        )
                return prefix + Tuils.SPACE + oldLabel
            } else {
//                    two dots or more, the second word is the company name
                prefix = packageName.substring(firstDot, secondDot + firstDot)
                prefix =
                    prefix.substring(0, 1).uppercase(Locale.getDefault()) + prefix.substring(1)
                        .lowercase(
                            Locale.getDefault()
                        )
                return prefix + Tuils.SPACE + oldLabel
            }
        } catch (e: Exception) {
            return packageName
        }
    }

    @JvmStatic
    fun format(app: LaunchInfo, info: PackageInfo): String {
        val builder = StringBuilder()

        builder.append(info.packageName).append(Tuils.NEWLINE)
        builder.append("vrs: ").append(info.versionCode).append(" - ").append(info.versionName)
            .append(Tuils.NEWLINE).append(Tuils.NEWLINE)
        builder.append("launched_times: ").append(app.launchedTimes).append(Tuils.NEWLINE)
            .append(Tuils.NEWLINE)

        builder.append("Install: ").append(
            TimeManager.instance.replace(
                "%t0",
                info.firstInstallTime,
                Int.MAX_VALUE
            )
        ).append(Tuils.NEWLINE).append(Tuils.NEWLINE)

        val a = info.activities
        if (a != null && a.size > 0) {
            val `as`: MutableList<String?> = ArrayList<String?>()
            for (i in a) `as`.add(i.name.replace(info.packageName, Tuils.EMPTYSTRING))
            builder.append("Activities: ").append(Tuils.NEWLINE)
                .append(Tuils.toPlanString(`as`, Tuils.NEWLINE)).append(Tuils.NEWLINE)
                .append(Tuils.NEWLINE)
        }

        val s = info.services
        if (s != null && s.size > 0) {
            val ss: MutableList<String?> = ArrayList<String?>()
            for (i in s) ss.add(i.name.replace(info.packageName, Tuils.EMPTYSTRING))
            builder.append("Services: ").append(Tuils.NEWLINE)
                .append(Tuils.toPlanString(ss, Tuils.NEWLINE)).append(Tuils.NEWLINE)
                .append(Tuils.NEWLINE)
        }

        val r = info.receivers
        if (r != null && r.size > 0) {
            val rs: MutableList<String?> = ArrayList<String?>()
            for (i in r) rs.add(i.name.replace(info.packageName, Tuils.EMPTYSTRING))
            builder.append("Receivers: ").append(Tuils.NEWLINE)
                .append(Tuils.toPlanString(rs, Tuils.NEWLINE)).append(Tuils.NEWLINE)
                .append(Tuils.NEWLINE)
        }

        val p = info.requestedPermissions
        if (p != null && p.size > 0) {
            val ps: MutableList<String?> = ArrayList<String?>()
            for (i in p) ps.add(i.substring(i.lastIndexOf(".") + 1))
            builder.append("Permissions: ").append(Tuils.NEWLINE)
                .append(Tuils.toPlanString(ps, ", "))
        }

        return builder.toString()
    }

    @JvmStatic
    fun printApps(apps: MutableList<String>): String? {
        if (apps.size == 0) {
            return apps.toString()
        }

        val list: MutableList<String?> = ArrayList<String?>(apps)

        Collections.sort<String?>(
            list,
            Comparator { s1: String?, s2: String? -> Tuils.alphabeticCompare(s1, s2) })

        Tuils.addPrefix(list, Tuils.DOUBLE_SPACE)
        Tuils.insertHeaders(list, false)
        return Tuils.toPlanString(list)
    }


    @JvmStatic
    fun labelList(infos: MutableList<out Launchable>, sort: Boolean): MutableList<String> {
        val labels: MutableList<String> = ArrayList<String>()
        for (info in infos) {
            labels.add(info.name())
        }
        if (sort) Collections.sort<String>(labels)
        return labels
    }
}

class AppsManager(context: Context) : XMLPrefsElement {
    private val NAME = "APPS"
    private var file: File? = null

    private val SHOW_ATTRIBUTE = "show"
    private val APPS_ATTRIBUTE = "apps"
    private val BGCOLOR_ATTRIBUTE = "bgColor"
    private val FORECOLOR_ATTRIBUTE = "foreColor"
    private val context: Context?

    private var appsHolder: AppsHolder? = null
    private var hiddenLaunchables: MutableList<Launchable> = ArrayList()

    private val PREFS = "apps"
    private val preferences: SharedPreferences
    private val editor: SharedPreferences.Editor

    private var prefsList: XMLPrefsList? = null

    @JvmField
    var groups: MutableList<Group>

    private var pp: Pattern? = null
    private var pl: Pattern? = null
    private var appInstalledFormat: String?
    private var appUninstalledFormat: String?
    var appInstalledColor: Int = 0
    var appUninstalledColor: Int = 0

    override fun delete(): Array<String?>? {
        return null
    }

    override fun write(c: Context?, save: XMLPrefsSave, value: String?) {
        XMLPrefsManager.set(
            File(Tuils.getFolder(c), PATH),
            save.label(),
            arrayOf<String>(XMLPrefsManager.VALUE_ATTRIBUTE),
            arrayOf<String?>(value)
        )
    }

    override fun path(): String {
        return PATH
    }

    override fun getValues(): XMLPrefsList {
        return prefsList!!
    }

    private val appsBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.getAction()
            val data = intent.getData()!!.getSchemeSpecificPart()
            if (action == Intent.ACTION_PACKAGE_ADDED) {
                appInstalled(data)
            } else {
                appUninstalled(data)
            }
        }
    }

    init {
        instance = this
        this.context = context

        appInstalledFormat =
            if (XMLPrefsManager.getBoolean(Ui.show_app_installed)) XMLPrefsManager.get(
                Behavior.app_installed_format
            ) else null
        appUninstalledFormat =
            if (XMLPrefsManager.getBoolean(Ui.show_app_uninstalled)) XMLPrefsManager.get(
                Behavior.app_uninstalled_format
            ) else null

        if (appInstalledFormat != null || appUninstalledFormat != null) {
            pp = Pattern.compile("%p", Pattern.CASE_INSENSITIVE)
            pl = Pattern.compile("%l", Pattern.CASE_INSENSITIVE)

            appInstalledColor = XMLPrefsManager.getColor(Theme.app_installed_color)
            appUninstalledColor = XMLPrefsManager.getColor(Theme.app_uninstalled_color)
        } else {
            pp = null
            pl = null
        }

        val root = Tuils.getFolder(context)
        if (root == null) this.file = null
        else this.file = File(root, PATH)

        this.preferences = context.getSharedPreferences(PREFS, 0)
        this.editor = preferences.edit()

        this.groups = ArrayList<Group>()

        initAppListener(context)

        object : StoppableThread() {
            override fun run() {
                super.run()

                fill()
                LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(
                    Intent(
                        UIManager.ACTION_UPDATE_SUGGESTIONS
                    )
                )
            }
        }.start()
    }

    private fun initAppListener(c: Context) {
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED)
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        intentFilter.addDataScheme("package")

        c.registerReceiver(appsBroadcast, intentFilter)
    }

    fun fill() {
        val allApps = createAppMap(context!!.packageManager).toMutableList()
        hiddenLaunchables = ArrayList()
        groups.clear()

        val currentFile = file ?: run {
            Tuils.sendOutput(Color.RED, context, R.string.tuinotfound_app)
            return
        }

        try {
            if (!currentFile.exists()) {
                XMLPrefsManager.resetFile(currentFile, NAME)
            }

            val o = XMLPrefsManager.buildDocument(currentFile, NAME)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return
            }
            val document = o[0] as Document
            val root = o[1] as Element

            val remainingEnums = Apps.entries.toMutableList()

            processXmlNodes(root, allApps, remainingEnums)
            handleMissingPreferences(document, root, currentFile, remainingEnums)
            syncLaunchCounts(allApps)

            // Final State Update
            appsHolder = AppsHolder(allApps, prefsList!!)

            finalizeGroups()

        } catch (e: Exception) {
            Tuils.log(e)
            Tuils.toFile(this.context, e)
        }
    }

    private fun handleMissingPreferences(
        document: Document,
        root: Element,
        file: File,
        remainingEnums: List<Apps>
    ) {
        if (remainingEnums.isNotEmpty()) {
            for (appEnum in remainingEnums) {
                val defaultValue = appEnum.defaultValue()
                val label = appEnum.label()
                val newElement = document.createElement(label)
                newElement.setAttribute(XMLPrefsManager.VALUE_ATTRIBUTE, defaultValue)
                root.appendChild(newElement)
                prefsList?.add(label, defaultValue)
            }
            XMLPrefsManager.writeTo(document, file)
        }
    }

    private fun processXmlNodes(root: Element, allApps: MutableList<Launchable>, enums: MutableList<Apps>) {
        prefsList = XMLPrefsList()
        val nodes = root.getElementsByTagName("*")

        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            val nodeName = node.nodeName
            val enumMatch = enums.find { it.label() == nodeName }

            if (enumMatch != null) {
                handlePreferenceNode(node, nodeName)
                enums.remove(enumMatch)
            } else if (node is Element) {
                handleCustomElement(node, allApps)
            }
        }
    }

    private fun handlePreferenceNode(node: Node, name: String) {
        val value = if (name.startsWith("d")) {
            node.attributes.getNamedItem(XMLPrefsManager.VALUE_ATTRIBUTE).nodeValue
        } else {
            XMLPrefsManager.getStringAttribute(node as Element, XMLPrefsManager.VALUE_ATTRIBUTE)
        }
        prefsList?.add(name, value)
    }

    private fun handleCustomElement(element: Element, allApps: MutableList<Launchable>) {
        if (element.hasAttribute(APPS_ATTRIBUTE)) {
            createGroupFromElement(element, allApps)
        } else {
            handleHiddenApp(element, allApps)
        }
    }

    private fun createGroupFromElement(e: Element, allApps: List<Launchable>) {
        val name = e.nodeName
        if (name.contains(Tuils.SPACE)) {
            Tuils.sendOutput(Color.RED, context, "${PATH}: ${context?.getString(R.string.output_groupspace)}: $name")
            return
        }

        val group = Group(name).apply {
            val appNames = e.getAttribute(APPS_ATTRIBUTE).split(APPS_SEPARATOR).filter { it.isNotEmpty() }
            val available = allApps.toMutableList()

            appNames.forEach { s ->
                val match = available.find { it.names().contains(s) }
                if (match != null) {
                    add(match, false)
                    available.remove(match)
                }
            }

            sort()
            parseColorAttribute(e, BGCOLOR_ATTRIBUTE)?.let { bgColor = it }
            parseColorAttribute(e, FORECOLOR_ATTRIBUTE)?.let { foreColor = it }
        }
        groups.add(group)
    }

    private fun handleHiddenApp(element: Element, allApps: MutableList<Launchable>) {
        val isShown = !element.hasAttribute(SHOW_ATTRIBUTE) || element.getAttribute(SHOW_ATTRIBUTE).toBoolean()
        if (!isShown) {
            val key = element.nodeName
            val match = allApps.find { launchable ->
                val launchableKey = when (launchable) {
                    is AppLauncher -> "${launchable.packageName}-${launchable.activityName}"
                    is WebLauncher -> launchable.name
                }
                launchableKey == key
            }
            if (match != null) {
                allApps.remove(match)
                hiddenLaunchables.add(match)
            }
        }
    }

    private fun syncLaunchCounts(allApps: List<Launchable>) {
        preferences.all.forEach { (key, value) ->
            if (value is Int) {
                val match = allApps.find { launchable ->
                    val launchableKey = when (launchable) {
                        is AppLauncher -> "${launchable.packageName}-${launchable.activityName}"
                        is WebLauncher -> launchable.name
                    }
                    launchableKey == key
                }
                match?.launchTimes = value
            }
        }
    }

    private fun finalizeGroups() {
        Group.sorting = XMLPrefsManager.getInt(Apps.app_groups_sorting)
        groups.forEach { it.sort() }
        groups.sortWith { o1, o2 -> Tuils.alphabeticCompare(o1.name(), o2.name()) }
    }

    // Helper to extract color parsing logic
    private fun parseColorAttribute(e: Element, attr: String): Int? {
        if (e.hasAttribute(attr)) {
            val colorStr = e.getAttribute(attr)
            if (colorStr.isNotEmpty()) {
                return try { Color.parseColor(colorStr) } catch (err: Exception) {
                    Tuils.sendOutput(Color.RED, context, "${PATH}: ${context?.getString(R.string.output_invalidcolor)}: $colorStr")
                    null
                }
            }
        }
        return null
    }

    // Helper to centralize the ComponentName search logic
    private fun findComponent(key: String, allApps: List<Launchable>): ComponentName? {
        val split = key.split("-").filter { it.isNotEmpty() }
        return when {
            split.size >= 2 -> ComponentName(split[0], split[1])
            split.size == 1 -> {
                allApps.find { launchable ->
                    when (launchable) {
                        is AppLauncher -> {
                            if (split[0].contains("Activity")) {
                                launchable.activityName.contains(split[0])
                            } else {
                                launchable.packageName == split[0]
                            }
                        }
                        else -> false
                    }
                }?.componentName
            }
            else -> null
        }
    }

    private fun createAppMap(mgr: PackageManager): MutableList<Launchable> {
        val launchables: MutableList<Launchable> = ArrayList<Launchable>()
        val i = Intent(Intent.ACTION_MAIN)
        i.addCategory(Intent.CATEGORY_LAUNCHER)

        val main: List<ResolveInfo>
        try {
            main = mgr.queryIntentActivities(i, 0)
        } catch (e: Exception) {
            return launchables
        }

        // Note: For SIM TOolkit, the name 'SIM Toolkit' is defined as one of the shortcuts so not the actual app name found by
        // resovleinfo.loadLabel(mgr), so ensure T-UI is the default for this to work
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && Tuils.isMyLauncherDefault(context!!.getPackageManager())) {
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            for (ri in main) {
                val appLauncher = AppLauncher(
                    ri.activityInfo.packageName,
                    ri.activityInfo.name,
                    ri.loadLabel(mgr).toString()
                )
                try {
                    val query = ShortcutQuery()
                    query.setQueryFlags(ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC)
                    query.setPackage(appLauncher.packageName)
                    val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                    if (shortcuts != null && shortcuts.isNotEmpty()) {
                        appLauncher.shortcut = shortcuts[0].id
                    }
                } catch (e: SecurityException) {
                    Log.e(APP_TAG, e.toString())
                } catch (e: Throwable) {
                    // t-ui is not the default launcher
                    Log.e(APP_TAG, e.toString())
                }

                launchables.add(appLauncher)
            }
        } else {
            for (ri in main) {
                val appLauncher = AppLauncher(
                    ri.activityInfo.packageName,
                    ri.activityInfo.name,
                    ri.loadLabel(mgr).toString()
                )
                launchables.add(appLauncher)
            }
        }

        val webApps = loadWebAppsForAppsManager()
        launchables.addAll(webApps)

        return launchables
    }

    fun loadWebAppsForAppsManager(fileName: String = "web_app.xml"): List<WebLauncher> {
        return try {
            val file = File(Tuils.getFolder(context), fileName)
            if (file.exists()) {
                val xmlString = file.readText()
                Launchable.listFromXml(xmlString).filterIsInstance<WebLauncher>()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(APP_TAG, "Error loading web apps", e)
            emptyList()
        }
    }

    fun saveWebAppsForAppsManager(webLaunchers: List<WebLauncher>, fileName: String = "web_app.xml") {
        try {
            val file = File(Tuils.getFolder(context), fileName)
            val xmlString = Launchable.listToXml(webLaunchers)
            file.writeText(xmlString)
        } catch (e: Exception) {
            Log.e(APP_TAG, "Error saving web apps", e)
        }
    }

    fun addWebApp(name: String, url: String, fileName: String = "web_app.xml"): Boolean {
        return try {
            val currentWebApps = loadWebAppsForAppsManager(fileName).toMutableList()
            val existingIndex = currentWebApps.indexOfFirst { it.name.equals(name, ignoreCase = true) }
            if (existingIndex != -1) {
                currentWebApps[existingIndex] = WebLauncher(name, url)
            } else {
                currentWebApps.add(WebLauncher(name, url))
            }
            saveWebAppsForAppsManager(currentWebApps, fileName)
            true
        } catch (e: Exception) {
            Log.e(APP_TAG, "Error adding web app", e)
            false
        }
    }

    private fun appInstalled(packageName: String) {
        try {
            val manager = context!!.getPackageManager()

            val packageInfo = manager.getPackageInfo(packageName, 0)

            if (appInstalledFormat != null) {
                var cp = appInstalledFormat!!

                cp = pp!!.matcher(cp).replaceAll(packageName)
                if (packageInfo != null) {
                    val sequence = packageInfo.applicationInfo.loadLabel(manager)
                    if (sequence != null) cp = pl!!.matcher(cp).replaceAll(sequence.toString())
                } else {
                    val index = packageName.lastIndexOf(Tuils.DOT)
                    if (index == -1) cp = pl!!.matcher(cp).replaceAll(Tuils.EMPTYSTRING)
                    else {
                        cp = pl!!.matcher(cp).replaceAll(packageName.substring(index + 1))
                    }
                }

                cp = Tuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE)

                Tuils.sendOutput(appInstalledColor, context, cp)
            }

            val i = manager.getLaunchIntentForPackage(packageName)
            if (i == null) return

            val name = i.getComponent()
            val activity = name!!.getClassName()
            val label = manager.getActivityInfo(name, 0).loadLabel(manager).toString()

            val app = AppLauncher(packageName, activity, label)
            appsHolder!!.add(app)
        } catch (e: Exception) {
        }
    }

    private fun appUninstalled(packageName: String) {
        if (appsHolder == null || context == null) return

        val infos = appsHolder!!.launchables.filter { 
            (it as? AppLauncher)?.packageName == packageName 
        }

        if (appUninstalledFormat != null) {
            var cp = appUninstalledFormat!!

            cp = pp!!.matcher(cp).replaceAll(packageName)
            if (infos.isNotEmpty()) {
                cp = pl!!.matcher(cp).replaceAll(infos[0].name())
            } else {
                val index = packageName.lastIndexOf(Tuils.DOT)
                if (index == -1) cp = pl!!.matcher(cp).replaceAll(Tuils.EMPTYSTRING)
                else {
                    cp = pl!!.matcher(cp).replaceAll(packageName.substring(index + 1))
                }
            }
            cp = Tuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE)

            Tuils.sendOutput(appUninstalledColor, context, cp)
        }

        for (i in infos) appsHolder!!.remove(i)
    }

    fun findLaunchableWithLabel(label: String, type: Int): Launchable? {
        if (appsHolder == null) return null

        val appList: MutableList<Launchable>?
        if (type == SHOWN_APPS) {
            appList = appsHolder!!.launchables
        } else {
            appList = hiddenLaunchables
        }

        if (appList == null) return null

        val labelLower = label.lowercase(Locale.getDefault())
        val unspacedLabel = Tuils.removeSpaces(labelLower)
        
        return appList.find { launchable ->
            launchable.names().any { it.lowercase(Locale.getDefault()) == labelLower } ||
            Tuils.removeSpaces(launchable.name().lowercase(Locale.getDefault())) == unspacedLabel
        }
    }

    fun findLaunchableWithLabel(label: String): Launchable? {
        return findLaunchableWithLabel(label, SHOWN_APPS)
    }

    fun writeLaunchTimes(info: LaunchInfo) {
        editor.putInt(info.write(), info.launchedTimes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            editor.apply()
        } else {
            editor.commit()
        }

        if (appsHolder != null) appsHolder!!.update(true)
    }

    fun writeLaunchTimes(launchable: Launchable) {
        val key = when (launchable) {
            is AppLauncher -> "${launchable.packageName}-${launchable.activityName}"
            is WebLauncher -> launchable.name
        }
        editor.putInt(key, launchable.launchTimes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            editor.apply()
        } else {
            editor.commit()
        }

        if (appsHolder != null) appsHolder!!.update(true)
    }

    fun getIntent(info: LaunchInfo): Intent {
        info.launchedTimes++
        object : StoppableThread() {
            override fun run() {
                super.run()

                appsHolder!!.requestSuggestionUpdate(info)
                writeLaunchTimes(info)
            }
        }.start()

        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(info.componentName)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    fun getIntent(launchable: Launchable): Intent {
        launchable.launchTimes++
        object : StoppableThread() {
            override fun run() {
                super.run()
                writeLaunchTimes(launchable)
            }
        }.start()

        return launchable.toIntent(context!!)
    }

    fun performLaunch(launchable: Launchable): Boolean {
        launchable.launch(context!!)
        return true
    }

    fun hideActivity(info: LaunchInfo): String? {
        XMLPrefsManager.set(
            file,
            info.write(),
            arrayOf<String>(SHOW_ATTRIBUTE),
            arrayOf<String>(false.toString() + Tuils.EMPTYSTRING)
        )

        appsHolder!!.remove(info)
        appsHolder!!.update(true)
        hiddenLaunchables!!.add(info)
        AppUtils.checkEquality(hiddenLaunchables!!)

        return info.publicLabel
    }

    fun showActivity(info: LaunchInfo): String? {
        XMLPrefsManager.set(
            file,
            info.write(),
            arrayOf<String>(SHOW_ATTRIBUTE),
            arrayOf<String>(true.toString() + Tuils.EMPTYSTRING)
        )

        hiddenLaunchables!!.remove(info)
        appsHolder!!.add(info)
        appsHolder!!.update(false)

        return info.publicLabel
    }

    fun shownApps(): MutableList<Launchable> {
        return appsHolder?.launchables ?: mutableListOf()
    }

    fun hideActivity(launchable: Launchable): String? {
        XMLPrefsManager.set(
            file,
            launchable.write(),
            arrayOf(SHOW_ATTRIBUTE),
            arrayOf("false")
        )
        appsHolder!!.remove(launchable)
        appsHolder!!.update(true)
        hiddenLaunchables!!.add(launchable)
        return launchable.name()
    }

    fun showActivity(launchable: Launchable): String? {
        XMLPrefsManager.set(
            file,
            launchable.write(),
            arrayOf(SHOW_ATTRIBUTE),
            arrayOf("true")
        )
        hiddenLaunchables!!.remove(launchable)
        appsHolder!!.add(launchable)
        appsHolder!!.update(false)
        return launchable.name()
    }

    fun createGroup(name: String): String? {
        val index = Tuils.find(name, groups)
        if (index == -1) {
            groups.add(Group(name))
            return XMLPrefsManager.set(
                file,
                name,
                arrayOf<String>(APPS_ATTRIBUTE),
                arrayOf<String>(Tuils.EMPTYSTRING)
            )
        }

        return context!!.getString(R.string.output_groupexists)
    }

    fun groupBgColor(name: String?, color: String?): String? {
        val index = Tuils.find(name, groups)
        if (index == -1) {
            return context!!.getString(R.string.output_groupnotfound)
        }

        groups.get(index).bgColor = Color.parseColor(color)
        return XMLPrefsManager.set(
            file,
            name,
            arrayOf<String>(BGCOLOR_ATTRIBUTE),
            arrayOf<String?>(color)
        )
    }

    fun groupForeColor(name: String?, color: String?): String? {
        val index = Tuils.find(name, groups)
        if (index == -1) {
            return context!!.getString(R.string.output_groupnotfound)
        }

        groups.get(index).foreColor = Color.parseColor(color)
        return XMLPrefsManager.set(
            file,
            name,
            arrayOf<String>(FORECOLOR_ATTRIBUTE),
            arrayOf<String?>(color)
        )
    }

    fun removeGroup(name: String?): String? {
        val output = XMLPrefsManager.removeNode(file, name)

        if (output == null) return null
        if (output.length == 0) return context!!.getString(R.string.output_groupnotfound)

        val index = Tuils.find(name, groups)
        if (index != -1) groups.removeAt(index)

        return output
    }

    fun addAppToGroup(group: String?, app: LaunchInfo): String? {
        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return null
            }
        } catch (e: Exception) {
            return e.toString()
        }

        val d = o[0] as Document?
        val root = o[1] as Element?

        val node = XMLPrefsManager.findNode(root, group)
        if (node == null) return context!!.getString(R.string.output_groupnotfound)

        val e = node as Element
        var apps = e.getAttribute(APPS_ATTRIBUTE)

        if (apps != null && app.isInside(apps)) return null

        apps = apps + APPS_SEPARATOR + app.write()
        if (apps.startsWith(APPS_SEPARATOR)) apps = apps.substring(1)

        e.setAttribute(APPS_ATTRIBUTE, apps)

        XMLPrefsManager.writeTo(d, file)

        val index = Tuils.find(group, groups)
        if (index != -1) groups.get(index).add(app, true)

        return null
    }

    fun removeAppFromGroup(group: String?, app: LaunchInfo): String? {
        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return null
            }
        } catch (e: Exception) {
            return e.toString()
        }

        val d = o[0] as Document?
        val root = o[1] as Element?

        val node = XMLPrefsManager.findNode(root, group)
        if (node == null) return context!!.getString(R.string.output_groupnotfound)

        val e = node as Element

        var apps = e.getAttribute(APPS_ATTRIBUTE)
        if (apps == null) return null

        if (!app.isInside(apps)) return null

        val temp = apps.replace(app.write().toRegex(), Tuils.EMPTYSTRING)
        if (temp.length < apps.length) {
            apps = temp
            apps = apps.replace((APPS_SEPARATOR + APPS_SEPARATOR).toRegex(), APPS_SEPARATOR)
            if (apps.startsWith(APPS_SEPARATOR)) apps = apps.substring(1)
            if (apps.endsWith(APPS_SEPARATOR)) apps = apps.substring(0, apps.length - 1)

            e.setAttribute(APPS_ATTRIBUTE, apps)

            XMLPrefsManager.writeTo(d, file)

            val index = Tuils.find(group, groups)
            if (index != -1) groups.get(index).remove(app)
        }

        return null
    }

    fun addAppToGroup(group: String?, launchable: Launchable): String? {
        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return null
            }
        } catch (e: Exception) {
            return e.toString()
        }

        val d = o[0] as Document?
        val root = o[1] as Element?

        val node = XMLPrefsManager.findNode(root, group)
        if (node == null) return context!!.getString(R.string.output_groupnotfound)

        val e = node as Element
        var apps = e.getAttribute(APPS_ATTRIBUTE)

        val groupIndex = Tuils.find(group, groups)
        if (groupIndex != -1) {
            val existingApp = groups[groupIndex].apps.find { it.names().contains(launchable.name()) }
            if (existingApp != null) return null
        }

        apps = apps + APPS_SEPARATOR + launchable.write()
        if (apps.startsWith(APPS_SEPARATOR)) apps = apps.substring(1)

        e.setAttribute(APPS_ATTRIBUTE, apps)

        XMLPrefsManager.writeTo(d, file)

        val index = Tuils.find(group, groups)
        if (index != -1) groups.get(index).add(launchable, true)

        return null
    }

    fun removeAppFromGroup(group: String?, launchable: Launchable): String? {
        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return null
            }
        } catch (e: Exception) {
            return e.toString()
        }

        val d = o[0] as Document?
        val root = o[1] as Element?

        val node = XMLPrefsManager.findNode(root, group)
        if (node == null) return context!!.getString(R.string.output_groupnotfound)

        val e = node as Element

        var apps = e.getAttribute(APPS_ATTRIBUTE)
        if (apps == null) return null

        val writeKey = launchable.write()
        val split = apps.split(APPS_SEPARATOR).filter { it.isNotEmpty() }
        val found = split.any {
            it == writeKey || (launchable is AppLauncher && it == launchable.packageName)
        }
        if (!found) return null

        val temp = apps.replace(writeKey.toRegex(), Tuils.EMPTYSTRING)
        if (temp.length < apps.length) {
            apps = temp
            apps = apps.replace((APPS_SEPARATOR + APPS_SEPARATOR).toRegex(), APPS_SEPARATOR)
            if (apps.startsWith(APPS_SEPARATOR)) apps = apps.substring(1)
            if (apps.endsWith(APPS_SEPARATOR)) apps = apps.substring(0, apps.length - 1)

            e.setAttribute(APPS_ATTRIBUTE, apps)

            XMLPrefsManager.writeTo(d, file)

            val index = Tuils.find(group, groups)
            if (index != -1) groups.get(index).remove(launchable)
        }

        return null
    }

    fun listGroup(group: String?): String? {
        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return null
            }
        } catch (e: Exception) {
            return e.toString()
        }

        val root = o[1] as Element?

        val node = XMLPrefsManager.findNode(root, group)
        if (node == null) return context!!.getString(R.string.output_groupnotfound)

        val e = node as Element

        val apps = e.getAttribute(APPS_ATTRIBUTE)
        if (apps == null) return "[]"

        var labels = Tuils.EMPTYSTRING

        val manager = context!!.getPackageManager()
        val split: Array<String> =
            apps.split(APPS_SEPARATOR.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (s in split) {
            if (s.length == 0) continue

            val label: String?

            val name = LaunchInfo.componentInfo(s)
            if (name == null) {
                try {
                    label = manager.getApplicationInfo(s, 0).loadLabel(manager).toString()
                } catch (e1: Exception) {
                    continue
                }
            } else {
                try {
                    label = manager.getActivityInfo(name, 0).loadLabel(manager).toString()
                } catch (e1: Exception) {
                    continue
                }
            }

            labels = labels + Tuils.NEWLINE + label
        }

        return labels.trim { it <= ' ' }
    }

    fun listGroups(): String? {
        val o: Array<Any?>?
        try {
            o = XMLPrefsManager.buildDocument(file, null)
            if (o == null) {
                Tuils.sendXMLParseError(context, PATH)
                return null
            }
        } catch (e: Exception) {
            return e.toString()
        }

        val root = o[1] as Element

        var groups = Tuils.EMPTYSTRING

        val list = root.getElementsByTagName("*")
        for (count in 0 until list.getLength()) {
            val node = list.item(count)
            if (node !is Element) continue

            val e = node
            if (!e.hasAttribute(APPS_ATTRIBUTE)) continue

            groups = groups + Tuils.NEWLINE + e.getNodeName()
        }

        if (groups.length == 0) return "[]"
        return groups.trim { it <= ' ' }
    }

    fun shownApps(): MutableList<Launchable>? {
        if (appsHolder == null) return ArrayList()
        return appsHolder!!.launchables
    }

    fun hiddenApps(): MutableList<Launchable> {
        return hiddenLaunchables
    }

    val suggestedApps: Array<Launchable?>
        get() {
            if (appsHolder == null) return arrayOfNulls(0)
            return appsHolder!!.suggestedApps
        }

    val suggestedLaunchables: Array<Launchable?>
        get() {
            return suggestedApps.mapNotNull { it?.toLaunchable() }.toTypedArray()
        }

    fun printApps(type: Int): String? {
        return printNApps(type, -1)
    }

    fun printApps(type: Int, text: String): String? {
        var ok: Boolean
        var length = 0
        try {
            length = text.toInt()
            ok = true
        } catch (exc: NumberFormatException) {
            ok = false
        }

        if (ok) {
            return printNApps(type, length)
        } else {
            return printAppsThatBegins(type, text)
        }
    }

    private fun printNApps(type: Int, n: Int): String? {
        try {
            val labels: MutableList<String> = AppUtils.labelList(
                (if (type == SHOWN_APPS) appsHolder!!.launchables else hiddenLaunchables)!!,
                true
            )

            if (n >= 0) {
                val toRemove = labels.size - n
                if (toRemove <= 0) return "[]"

                for (c in 0 until toRemove) {
                    labels.removeAt(labels.size - 1)
                }
            }

            return AppUtils.printApps(labels)
        } catch (e: NullPointerException) {
            return "[]"
        }
    }

    private fun printAppsThatBegins(type: Int, withStr: String?): String? {
        var with = withStr
        try {
            val labels: MutableList<String> = AppUtils.labelList(
                (if (type == SHOWN_APPS) appsHolder!!.launchables else hiddenLaunchables)!!,
                true
            )

            if (with != null && with.isNotEmpty()) {
                with = with.lowercase(Locale.getDefault())

                val it: MutableIterator<String> = labels.iterator()
                while (it.hasNext()) {
                    if (!it.next().lowercase(Locale.getDefault()).startsWith(with)) it.remove()
                }
            }

            return AppUtils.printApps(labels)
        } catch (e: NullPointerException) {
            return "[]"
        }
    }

    fun unregisterReceiver(context: Context) {
        context.unregisterReceiver(appsBroadcast)
    }

    fun onDestroy() {
        if (context != null) {
            unregisterReceiver(context)
        }
    }

    class Group(var name: String) : MainManager.Group, StringableObject {
        var apps: MutableList<Launchable>

        @JvmField
        var bgColor: Int = Int.Companion.MAX_VALUE
        @JvmField
        var foreColor: Int = Int.Companion.MAX_VALUE

        var lowercaseName: String

        init {
            this.lowercaseName = name.lowercase(Locale.getDefault())
            apps = ArrayList()
        }

        fun add(launchable: Launchable, sort: Boolean) {
            apps.add(launchable)
            if (sort) sort()
        }

        fun remove(launchable: Launchable) {
            apps.removeIf { it.componentName == launchable.componentName }
        }

        fun remove(app: String?) {
            apps.removeIf { launchable ->
                when (launchable) {
                    is AppLauncher -> launchable.packageName == app
                    else -> false
                }
            }
        }

        fun sort() {
            Collections.sort(apps, comparator)
        }

        fun contains(launchable: Launchable?): Boolean {
            return apps.any { it.componentName == launchable?.componentName }
        }

        override fun members(): MutableList<out Any?>? {
            return apps as MutableList<out Any?>
        }

        override fun use(mainPack: MainPack?, input: String?): Boolean {
            if (mainPack == null || input == null) return false
            val label = Tuils.removeSpaces(input)
            val launchable = apps.find { it.names().contains(label) }
            return launchable?.let { mainPack.appsManager.performLaunch(it) } ?: false
        }

        override fun name(): String? {
            return name
        }

        override fun getString(): String? {
            return name
        }

        override fun getLowercaseString(): String? {
            return lowercaseName
        }

        companion object {
            const val ALPHABETIC_UP_DOWN: Int = 0
            const val ALPHABETIC_DOWN_UP: Int = 1
            const val TIME_UP_DOWN: Int = 2
            const val TIME_DOWN_UP: Int = 3
            const val MOSTUSED_UP_DOWN: Int = 4
            const val MOSTUSED_DOWN_UP: Int = 5

            var sorting: Int = 0

            var comparator: Comparator<Launchable> = object : Comparator<Launchable> {
                override fun compare(o1: Launchable?, o2: Launchable?): Int {
                    if (o1 == null || o2 == null) {
                        return 0
                    }
                    when (sorting) {
                        ALPHABETIC_UP_DOWN -> return Tuils.alphabeticCompare(o1.name(), o2.name())
                        ALPHABETIC_DOWN_UP -> return Tuils.alphabeticCompare(o2.name(), o1.name())
                        TIME_UP_DOWN -> return o1.launchTimes - o2.launchTimes
                        TIME_DOWN_UP -> return o2.launchTimes - o1.launchTimes
                        MOSTUSED_UP_DOWN -> return o1.launchTimes - o2.launchTimes
                        MOSTUSED_DOWN_UP -> return o2.launchTimes - o1.launchTimes
                    }
                    return 0
                }
            }
        }
    }

    private inner class AppsHolder(
        var launchables: MutableList<Launchable>,
        private val values: XMLPrefsList
    ) {
        val MOST_USED: Int = 10
        val NULL: Int = 11
        val USER_DEFINIED: Int = 12

        private var suggestedAppMgr: SuggestedAppMgr? = null

        private inner class SuggestedAppMgr(values: XMLPrefsList, launchables: MutableList<Launchable>) {
            private var suggested: MutableList<SuggestedApp>
            private var lastWriteable = -1

            init {
                suggested = ArrayList<SuggestedApp>()

                val PREFIX = "default_app_n"
                for (count in 0..4) {
                    val vl = values.get(Apps.valueOf(PREFIX + (count + 1))).value

                    if (vl == Apps.NULL) continue
                    if (vl == Apps.MOST_USED) suggested.add(SuggestedApp(MOST_USED, count + 1))
                    else {
                        val match = launchables.find { launchable ->
                            val key = when (launchable) {
                                is AppLauncher -> "${launchable.packageName}-${launchable.activityName}"
                                is WebLauncher -> launchable.name
                            }
                            key == vl
                        }
                        if (match != null) {
                            suggested.add(SuggestedApp(match, USER_DEFINIED, count + 1))
                        }
                    }
                }

                sort()
            }

            fun size(): Int {
                return suggested.size
            }

            fun sort() {
                Collections.sort(suggested)
                for (count in suggested.indices) {
                    if (suggested[count].type != MOST_USED) {
                        lastWriteable = count - 1
                        return
                    }
                }
                lastWriteable = suggested.size - 1
            }

            fun get(index: Int): SuggestedApp {
                return suggested[index]
            }

            fun set(index: Int, launchable: Launchable?) {
                suggested[index].change(launchable)
            }

            fun attemptInsertSuggestion(launchable: Launchable) {
                if (launchable.launchTimes == 0 || lastWriteable == -1) {
                    return
                }

                val index = suggested.indexOfFirst { it.launchable?.componentName == launchable.componentName }
                if (index == -1) {
                    for (count in 0..lastWriteable) {
                        val app = get(count)

                        if (app.launchable == null || launchable.launchTimes > app.launchable!!.launchTimes) {
                            val before = app.launchable
                            suggested[count].change(launchable)

                            if (before != null) {
                                attemptInsertSuggestion(before)
                            }

                            break
                        }
                    }
                }
            }

            fun change(app: Launchable?, index: Int) {
                suggested[index].change(app)
            }

            fun requestUpdate(info: Launchable?) {
                if (info == null) return

                if (suggested.size == 0) return
                val index = Tuils.find(info, suggested)
                if (index == -1) {
                    if (suggested.size < 5) {
                        suggested.add(SuggestedApp(info, USER_DEFINIED, suggested.size + 1))
                        sort()
                    } else {
                        attemptInsertSuggestion(info)
                    }
                }
            }
        }

        fun add(app: Launchable) {
            launchables.add(app)
        }

        fun remove(app: Launchable?) {
            if (app == null) return
            val iterator = launchables.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().componentName == app.componentName) {
                    iterator.remove()
                    return
                }
            }
        }

        fun update(sort: Boolean) {
            if (suggestedAppMgr == null) {
                suggestedAppMgr = SuggestedAppMgr(values, launchables)
            }

            if (sort) {
                Collections.sort(launchables)
            }

            LocalBroadcastManager.getInstance(context!!.applicationContext)
                .sendBroadcast(Intent(UIManager.ACTION_UPDATE_SUGGESTIONS))
        }

        fun requestSuggestionUpdate(info: Launchable) {
            suggestedAppMgr?.requestUpdate(info)
        }

        val suggestedApps: Array<Launchable?>
            get() {
                if (suggestedAppMgr == null) {
                    suggestedAppMgr = SuggestedAppMgr(values, launchables)
                }

                val result = arrayOfNulls<Launchable>(suggestedAppMgr!!.size())
                for (count in 0 until suggestedAppMgr!!.size()) {
                    result[count] = suggestedAppMgr!!.get(count).launchable
                }
                return result
            }

        companion object {
            private fun <T> find(obj: T, list: List<T>): Int {
                for (count in list.indices) {
                    if (list[count] == obj) return count
                }
                return -1
            }
        }
    }

    data class SuggestedApp(var launchable: Launchable?, var type: Int, var index: Int) : Comparable<SuggestedApp> {
        fun change(launchable: Launchable?) {
            this.launchable = launchable
        }

        override fun compareTo(other: SuggestedApp): Int {
            val a = launchable
            val b = other.launchable
            if (a == null && b == null) return 0
            if (a == null) return -1
            if (b == null) return 1
            return if (type == other.type) {
                b.launchTimes - a.launchTimes
            } else {
                type - other.type
            }
        }
    }

    companion object {
        const val SHOWN_APPS: Int = 10
        const val HIDDEN_APPS: Int = 11

        const val PATH: String = "apps.xml"
        const val APPS_SEPARATOR = ";"

        @JvmField
        var instance: AppsManager? = null
    }
}
