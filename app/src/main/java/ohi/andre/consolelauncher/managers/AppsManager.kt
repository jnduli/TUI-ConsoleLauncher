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
import android.text.TextUtils
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import it.andreuzzi.comparestring2.StringableObject
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlinx.serialization.Serializable
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.UIManager
import ohi.andre.consolelauncher.commands.CommandContext
import ohi.andre.consolelauncher.commands.CommandResult
import ohi.andre.consolelauncher.managers.launchable.AppLauncher
import ohi.andre.consolelauncher.managers.launchable.Launchable
import ohi.andre.consolelauncher.managers.launchable.WebLauncher
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.classes.SharedPrefsXMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsElement
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsList
import ohi.andre.consolelauncher.managers.xml.classes.XMLPrefsSave
import ohi.andre.consolelauncher.managers.xml.classes.loadJsonList
import ohi.andre.consolelauncher.managers.xml.classes.saveJsonList
import ohi.andre.consolelauncher.managers.xml.options.Apps
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.StoppableThread
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.SystemUtils
import ohi.andre.consolelauncher.tuils.FileTuils
import ohi.andre.consolelauncher.tuils.LogTuils
import ohi.andre.consolelauncher.tuils.OutputTuils
import ohi.andre.consolelauncher.tuils.StringTuils

import java.io.File
import java.util.Collections
import java.util.Locale
import java.util.regex.Pattern


private val APP_TAG: String = "AppsManager"

class AppsManager(context: Context) : XMLPrefsElement {
    private val context: Context = context

    private var appsHolder: AppsHolder? = null
    private var hiddenLaunchables: MutableList<Launchable> = ArrayList()

    private val PREFS = "apps"
    private val preferences: SharedPreferences
    private val editor: SharedPreferences.Editor

    private var prefsList: XMLPrefsList

    @JvmField
    var groups: MutableList<Group>

    private val pp: Pattern = Pattern.compile("%p", Pattern.CASE_INSENSITIVE)
    private val pl: Pattern = Pattern.compile("%l", Pattern.CASE_INSENSITIVE)
    private var appInstalledFormat: String?
    private var appUninstalledFormat: String?
    var appInstalledColor: Int = 0
    var appUninstalledColor: Int = 0

    override fun delete(): Array<String?>? {
        return null
    }

    override fun write(c: Context?, save: XMLPrefsSave, value: String?) {
        prefsList.add(save.label(), value)
    }

    override fun path(): String {
        return PATH
    }

    override fun getValues(): XMLPrefsList {
        return prefsList
    }

    private val appsBroadcast: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action = intent.getAction()
            val data = intent.data?.schemeSpecificPart ?: return
            if (action == Intent.ACTION_PACKAGE_ADDED) {
                appInstalled(data)
            } else {
                appUninstalled(data)
            }
        }
    }

    init {
        instance = this

        appInstalledFormat =
            if (XMLPrefsManager.getBoolean(Ui.show_app_installed)) XMLPrefsManager.get(
                Behavior.app_installed_format
            ) else null
        appUninstalledFormat =
            if (XMLPrefsManager.getBoolean(Ui.show_app_uninstalled)) XMLPrefsManager.get(
                Behavior.app_uninstalled_format
            ) else null

        if (appInstalledFormat != null || appUninstalledFormat != null) {
            appInstalledColor = XMLPrefsManager.getColor(Theme.app_installed_color)
            appUninstalledColor = XMLPrefsManager.getColor(Theme.app_uninstalled_color)
        }

        val appsPrefs = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)
        this.prefsList = SharedPrefsXMLPrefsList(appsPrefs)
        XMLPrefsManager.seedSharedPrefsDefaults(appsPrefs, Apps.entries.toList())

        this.preferences = context.getSharedPreferences(PREFS, 0)
        this.editor = preferences.edit()

        this.groups = ArrayList<Group>()

        initAppListener(context)

        object : StoppableThread() {
            override fun run() {
                super.run()

                fill()
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
        val allApps = createAppMap(context.packageManager).toMutableList()
        hiddenLaunchables = ArrayList()
        groups.clear()

        try {
            val prefs = appsPrefs()
            XMLPrefsManager.seedSharedPrefsDefaults(prefs, Apps.entries.toList())

            applyHiddenApps(prefs, allApps)
            loadGroups(prefs, allApps)
            syncLaunchCounts(allApps)

            // Final State Update
            appsHolder = AppsHolder(allApps, prefsList)

            finalizeGroups()

            LocalBroadcastManager.getInstance(context.applicationContext).sendBroadcast(
                Intent(UIManager.ACTION_UPDATE_SUGGESTIONS)
            )

        } catch (e: Exception) {
            LogTuils.log(e)
            LogTuils.toFile(this.context, e)
        }
    }

    private fun appsPrefs(): SharedPreferences = context.getSharedPreferences(PATH, Context.MODE_PRIVATE)

    private fun applyHiddenApps(prefs: SharedPreferences, allApps: MutableList<Launchable>) {
        val hiddenKeys = loadJsonList<String>(prefs, HIDDEN_APPS_KEY)
        for (key in hiddenKeys) {
            val match = allApps.find { launchable -> launchable.storageKey() == key }
            if (match != null) {
                allApps.remove(match)
                hiddenLaunchables.add(match)
            }
        }
    }

    private fun loadGroups(prefs: SharedPreferences, allApps: List<Launchable>) {
        val records = loadJsonList<GroupRecord>(prefs, GROUPS_KEY)
        for (record in records) {
            if (record.name.contains(Tuils.SPACE)) {
                OutputTuils.sendOutput(context, "${PATH}: ${context.getString(R.string.output_groupspace)}: ${record.name}", Color.RED)
                continue
            }

            val group = Group(record.name, this).apply {
                val available = allApps.toMutableList()

                record.apps.forEach { s ->
                    val match = available.find { it.names().contains(s) }
                    if (match != null) {
                        add(match, false)
                        available.remove(match)
                    }
                }

                sort()
                bgColor = record.bgColor ?: Int.MAX_VALUE
                foreColor = record.foreColor ?: Int.MAX_VALUE
            }
            groups.add(group)
        }
    }

    private fun syncLaunchCounts(allApps: List<Launchable>) {
        preferences.all.forEach { (key, value) ->
            if (value is Int) {
                val match = allApps.find { launchable -> launchable.storageKey() == key }
                match?.launchTimes = value
            }
        }
    }

    private fun finalizeGroups() {
        Group.sorting = XMLPrefsManager.getInt(Apps.app_groups_sorting)
        groups.forEach { it.sort() }
        groups.sortWith { o1, o2 -> StringTuils.alphabeticCompare(o1.name, o2.name) }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && SystemUtils.isMyLauncherDefault(context.getPackageManager())) {
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
            val file = File(FileTuils.getFolder(context), fileName)
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
            val file = File(FileTuils.getFolder(context), fileName)
            val xmlString = Launchable.listToXml(webLaunchers)
            file.writeText(xmlString)
        } catch (e: Exception) {
            Log.e(APP_TAG, "Error saving web apps", e)
        }
    }

    fun listWebApps(fileName: String = "web_app.xml"): String {
        val webApps = loadWebAppsForAppsManager(fileName)
        if (webApps.isEmpty()) return "No web apps saved"
        return webApps.joinToString("\n") { "${it.name} -> ${it.url}" }
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
            val manager = context.getPackageManager()

            val packageInfo = manager.getPackageInfo(packageName, 0)

            appInstalledFormat?.let { format ->
                // the label is left as-is (unreplaced) when the package has no loadable label
                val label = packageInfo?.applicationInfo?.loadLabel(manager)?.toString()
                    ?: if (packageInfo == null) shortPackageName(packageName) else null
                OutputTuils.sendOutput(context, formatAppMessage(format, packageName, label), appInstalledColor)
            }

            val i = manager.getLaunchIntentForPackage(packageName) ?: return
            val name = i.component ?: return
            val label = manager.getActivityInfo(name, 0).loadLabel(manager).toString()

            appsHolder?.add(AppLauncher(packageName, name.className, label))
        } catch (e: Exception) {
        }
    }

    private fun appUninstalled(packageName: String) {
        val holder = appsHolder ?: return

        val infos = holder.launchables.filter {
            (it as? AppLauncher)?.packageName == packageName
        }

        appUninstalledFormat?.let { format ->
            val label = infos.firstOrNull()?.name() ?: shortPackageName(packageName)
            OutputTuils.sendOutput(context, formatAppMessage(format, packageName, label), appUninstalledColor)
        }

        for (i in infos) holder.remove(i)
    }

    /** Fills `%p` (package name) and `%l` (label; left untouched when [label] is null) in an app installed/uninstalled message. */
    private fun formatAppMessage(format: String, packageName: String, label: String?): String {
        var cp = pp.matcher(format).replaceAll(packageName)
        if (label != null) cp = pl.matcher(cp).replaceAll(label)
        return StringTuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE)
    }

    /** The last segment of a package name (`com.example.app` → `app`), used when no label is available. */
    private fun shortPackageName(packageName: String): String {
        val index = packageName.lastIndexOf(Tuils.DOT)
        return if (index == -1) Tuils.EMPTYSTRING else packageName.substring(index + 1)
    }

    fun findLaunchableWithLabel(label: String, type: Int): Launchable? {
        val appList: MutableList<Launchable> =
            if (type == SHOWN_APPS) appsHolder?.launchables ?: return null else hiddenLaunchables

        val labelLower = label.lowercase(Locale.getDefault())
        val unspacedLabel = StringTuils.removeSpaces(labelLower)
        
        return appList.find { launchable ->
            launchable.names().any { it.lowercase(Locale.getDefault()) == labelLower } ||
            StringTuils.removeSpaces(launchable.name().lowercase(Locale.getDefault())) == unspacedLabel
        }
    }

    fun findLaunchableWithLabel(label: String): Launchable? {
        return findLaunchableWithLabel(label, SHOWN_APPS)
    }

    fun writeLaunchTimes(launchable: Launchable) {
        val key = launchable.storageKey()
        editor.putInt(key, launchable.launchTimes)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            editor.apply()
        } else {
            editor.commit()
        }

        appsHolder?.update(true)
    }

    fun getIntent(launchable: Launchable): Intent {
        launchable.launchTimes++
        object : StoppableThread() {
            override fun run() {
                super.run()
                writeLaunchTimes(launchable)
            }
        }.start()

        return launchable.toIntent(context)
    }

    fun performLaunch(launchable: Launchable): CommandResult = launchable.launch(context)

    private fun loadGroupRecords(): MutableList<GroupRecord> = loadJsonList(appsPrefs(), GROUPS_KEY)
    private fun saveGroupRecords(records: List<GroupRecord>) = saveJsonList(appsPrefs(), GROUPS_KEY, records)

    private fun groupToRecord(group: Group): GroupRecord = GroupRecord(
        name = group.name,
        apps = group.apps.map { it.storageKey() },
        bgColor = group.bgColor.takeIf { it != Int.MAX_VALUE },
        foreColor = group.foreColor.takeIf { it != Int.MAX_VALUE },
    )

    fun hideActivity(launchable: Launchable): String? {
        val prefs = appsPrefs()
        val keys = loadJsonList<String>(prefs, HIDDEN_APPS_KEY)
        val key = launchable.storageKey()
        if (!keys.contains(key)) keys.add(key)
        saveJsonList(prefs, HIDDEN_APPS_KEY, keys)

        appsHolder?.run {
            remove(launchable)
            update(true)
        }
        hiddenLaunchables.add(launchable)
        return launchable.name()
    }

    fun showActivity(launchable: Launchable): String? {
        val prefs = appsPrefs()
        val keys = loadJsonList<String>(prefs, HIDDEN_APPS_KEY)
        keys.remove(launchable.storageKey())
        saveJsonList(prefs, HIDDEN_APPS_KEY, keys)

        hiddenLaunchables.remove(launchable)
        appsHolder?.run {
            add(launchable)
            update(false)
        }
        return launchable.name()
    }

    fun createGroup(name: String): String? {
        val index = StringTuils.find(name, groups)
        if (index == -1) {
            val group = Group(name, this)
            groups.add(group)

            val records = loadGroupRecords()
            records.add(groupToRecord(group))
            saveGroupRecords(records)
            return null
        }

        return context.getString(R.string.output_groupexists)
    }

    fun groupBgColor(name: String?, color: String?): String? {
        val index = StringTuils.find(name, groups)
        if (index == -1) {
            return context.getString(R.string.output_groupnotfound)
        }

        groups.get(index).bgColor = Color.parseColor(color)

        val records = loadGroupRecords()
        val recordIndex = records.indexOfFirst { it.name == name }
        if (recordIndex >= 0) records[recordIndex] = groupToRecord(groups[index])
        saveGroupRecords(records)
        return null
    }

    fun groupForeColor(name: String?, color: String?): String? {
        val index = StringTuils.find(name, groups)
        if (index == -1) {
            return context.getString(R.string.output_groupnotfound)
        }

        groups.get(index).foreColor = Color.parseColor(color)

        val records = loadGroupRecords()
        val recordIndex = records.indexOfFirst { it.name == name }
        if (recordIndex >= 0) records[recordIndex] = groupToRecord(groups[index])
        saveGroupRecords(records)
        return null
    }

    fun removeGroup(name: String?): String? {
        val records = loadGroupRecords()
        val removed = records.removeIf { it.name == name }
        if (!removed) return context.getString(R.string.output_groupnotfound)
        saveGroupRecords(records)

        val index = StringTuils.find(name, groups)
        if (index != -1) groups.removeAt(index)

        return null
    }

    fun addAppToGroup(group: String?, launchable: Launchable): String? {
        val index = StringTuils.find(group, groups)
        if (index == -1) return context.getString(R.string.output_groupnotfound)

        val existingApp = groups[index].apps.find { it.names().contains(launchable.name()) }
        if (existingApp != null) return null

        groups[index].add(launchable, true)

        val records = loadGroupRecords()
        val recordIndex = records.indexOfFirst { it.name == group }
        if (recordIndex >= 0) records[recordIndex] = groupToRecord(groups[index])
        saveGroupRecords(records)

        return null
    }

    fun removeAppFromGroup(group: String?, launchable: Launchable): String? {
        val index = StringTuils.find(group, groups)
        if (index == -1) return context.getString(R.string.output_groupnotfound)

        val writeKey = launchable.storageKey()
        val found = groups[index].apps.any {
            it.storageKey() == writeKey || (launchable is AppLauncher && it is AppLauncher && it.packageName == launchable.packageName)
        }
        if (!found) return null

        groups[index].remove(launchable)

        val records = loadGroupRecords()
        val recordIndex = records.indexOfFirst { it.name == group }
        if (recordIndex >= 0) records[recordIndex] = groupToRecord(groups[index])
        saveGroupRecords(records)

        return null
    }

    fun listGroup(group: String?): String? {
        val index = StringTuils.find(group, groups)
        if (index == -1) return context.getString(R.string.output_groupnotfound)

        val labels = groups[index].apps.joinToString(Tuils.NEWLINE) { it.name() ?: Tuils.EMPTYSTRING }
        return labels.trim { it <= ' ' }.ifEmpty { "[]" }
    }

    fun listGroups(): String? {
        if (groups.isEmpty()) return "[]"
        return groups.joinToString(Tuils.NEWLINE) { it.name }.trim { it <= ' ' }
    }

    fun shownApps(): MutableList<Launchable>? {
        return appsHolder?.launchables ?: ArrayList()
    }

    fun hiddenApps(): MutableList<Launchable> {
        return hiddenLaunchables
    }

    val suggestedApps: Array<Launchable?>
        get() {
            return appsHolder?.suggestedApps ?: arrayOfNulls(0)
        }

    val suggestedLaunchables: Array<Launchable?>
        get() {
            return suggestedApps
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
            val labels: MutableList<String> = Launchable.labelList(
                (if (type == SHOWN_APPS) appsHolder?.launchables ?: return "[]" else hiddenLaunchables),
                true
            )

            if (n >= 0) {
                val toRemove = labels.size - n
                if (toRemove <= 0) return "[]"

                for (c in 0 until toRemove) {
                    labels.removeAt(labels.size - 1)
                }
            }

            return StringTuils.printApps(labels)
        } catch (e: NullPointerException) {
            return "[]"
        }
    }

    private fun printAppsThatBegins(type: Int, withStr: String?): String? {
        var with = withStr
        try {
            val labels: MutableList<String> = Launchable.labelList(
                (if (type == SHOWN_APPS) appsHolder?.launchables ?: return "[]" else hiddenLaunchables),
                true
            )

            if (with != null && with.isNotEmpty()) {
                with = with.lowercase(Locale.getDefault())

                val it: MutableIterator<String> = labels.iterator()
                while (it.hasNext()) {
                    if (!it.next().lowercase(Locale.getDefault()).startsWith(with)) it.remove()
                }
            }

            return StringTuils.printApps(labels)
        } catch (e: NullPointerException) {
            return "[]"
        }
    }

    fun unregisterReceiver(context: Context) {
        context.unregisterReceiver(appsBroadcast)
    }

    fun onDestroy() {
        unregisterReceiver(context)
    }

    @Serializable
    data class GroupRecord(
        val name: String,
        val apps: List<String> = emptyList(),
        val bgColor: Int? = null,
        val foreColor: Int? = null,
    )

    class Group(var name: String, private val appsManager: AppsManager) : MainManager.Group, StringableObject {
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

        override fun use(mainPack: CommandContext?, input: String?): Boolean {
            if (mainPack == null || input == null) return false
            val label = StringTuils.removeSpaces(input)
            val launchable = apps.find { it.names().contains(label) }
            return launchable?.let { appsManager.performLaunch(it) !is CommandResult.Error } ?: false
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
                        ALPHABETIC_UP_DOWN -> return StringTuils.alphabeticCompare(o1.name(), o2.name())
                        ALPHABETIC_DOWN_UP -> return StringTuils.alphabeticCompare(o2.name(), o1.name())
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
                    val vl = values.get(Apps.valueOf(PREFIX + (count + 1)))?.value

                    if (vl == null || vl == Apps.NULL) continue
                    if (vl == Apps.MOST_USED) suggested.add(SuggestedApp(null, MOST_USED, count + 1))
                    else {
                        val match = launchables.find { launchable -> launchable.storageKey() == vl }
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

                        val before = app.launchable
                        if (before == null || launchable.launchTimes > before.launchTimes) {
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
                val index = StringTuils.find(info, suggested)
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
            suggestedAppMgr()

            if (sort) {
                Collections.sort(launchables)
            }

            LocalBroadcastManager.getInstance(context.applicationContext)
                .sendBroadcast(Intent(UIManager.ACTION_UPDATE_SUGGESTIONS))
        }

        fun requestSuggestionUpdate(info: Launchable) {
            suggestedAppMgr?.requestUpdate(info)
        }

        val suggestedApps: Array<Launchable?>
            get() {
                val mgr = suggestedAppMgr()
                return Array(mgr.size()) { mgr.get(it).launchable }
            }

        private fun suggestedAppMgr(): SuggestedAppMgr =
            suggestedAppMgr ?: SuggestedAppMgr(values, launchables).also { suggestedAppMgr = it }

        private fun <T> find(obj: T, list: List<T>): Int {
            for (count in list.indices) {
                if (list[count] == obj) return count
            }
            return -1
        }
    }

    @Parcelize
    data class SuggestedApp(var launchable: @RawValue Launchable?, var type: Int, var index: Int) : Comparable<SuggestedApp>, Parcelable {
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

        private const val HIDDEN_APPS_KEY = "hidden_apps"
        private const val GROUPS_KEY = "groups"

        const val DEFAULT_APP_SLOT_COUNT: Int = 5

        @JvmField
        var instance: AppsManager? = null
    }

    /** The raw stored value (a [Launchable.storageKey], [Apps.MOST_USED] or [Apps.NULL]) of a `default_app_nX` slot. */
    fun defaultAppSlotValue(index: Int): String? {
        return prefsList.get(Apps.valueOf("default_app_n$index"))?.value
    }

    /** A user-facing label for what's currently pinned (or not) in a `default_app_nX` slot. */
    fun defaultAppSlotLabel(index: Int): String {
        return when (val marker = defaultAppSlotValue(index)) {
            null, Apps.NULL -> "Empty"
            Apps.MOST_USED -> "Most used"
            else -> appsHolder?.launchables?.find { it.storageKey() == marker }?.name() ?: marker
        }
    }

    /** Pins [launchable] (or clears the slot, if null) into the `default_app_nX` no-input suggestion slot at [index] (1-based). */
    fun pinDefaultApp(context: Context, index: Int, launchable: Launchable?) {
        if (index < 1 || index > DEFAULT_APP_SLOT_COUNT) return

        val save: XMLPrefsSave = Apps.valueOf("default_app_n$index")
        write(context, save, launchable?.storageKey() ?: Apps.NULL)

        // write() only persists to disk; fill() reloads prefsList/appsHolder (and its cached
        // suggestedAppMgr) from disk so the no-input suggestion row reflects the change live.
        fill()
    }
}
