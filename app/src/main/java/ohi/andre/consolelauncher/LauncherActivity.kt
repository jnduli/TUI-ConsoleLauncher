package ohi.andre.consolelauncher

import android.Manifest
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Log
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import ohi.andre.consolelauncher.commands.tuixt.TuixtActivity
import ohi.andre.consolelauncher.managers.ContactManager
import ohi.andre.consolelauncher.managers.ContactManager.Contact
import ohi.andre.consolelauncher.managers.RegexManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.TuiLocationManager
import ohi.andre.consolelauncher.managers.notifications.KeeperService
import ohi.andre.consolelauncher.managers.notifications.NotificationManager
import ohi.andre.consolelauncher.managers.notifications.NotificationMonitorService
import ohi.andre.consolelauncher.managers.notifications.NotificationService
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager.Suggestion
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Notifications
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.Assist
import ohi.andre.consolelauncher.tuils.CustomExceptionHandler
import ohi.andre.consolelauncher.tuils.LongClickableSpan
import ohi.andre.consolelauncher.tuils.PrivateIOReceiver
import ohi.andre.consolelauncher.tuils.PublicIOReceiver
import ohi.andre.consolelauncher.tuils.SimpleMutableEntry
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.Inputable
import ohi.andre.consolelauncher.tuils.interfaces.Outputable
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable
import ohi.andre.consolelauncher.tuils.interfaces.Reloadable.ReloadMessageCategory
import java.util.LinkedList
import java.util.Queue

class LauncherActivity : AppCompatActivity(), Reloadable {
    private var ui: UIManager? = null
    private var main: MainManager? = null

    private var privateIOReceiver: PrivateIOReceiver? = null
    private var publicIOReceiver: PublicIOReceiver? = null

    private var openKeyboardOnStart = false
    private var canApplyTheme = false
    private var backButtonEnabled = false

    // Permissions we need to request
    private val REQUIRED_STORAGE_PERMISSIONS = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )



    private var categories: MutableSet<ReloadMessageCategory>? = null
    private val stopActivity = Runnable {
        dispose()
        finish()

        val startMain = Intent(Intent.ACTION_MAIN)
        startMain.addCategory(Intent.CATEGORY_HOME)

        var reloadMessage: CharSequence? = Tuils.EMPTYSTRING
        for (c in categories!!) {
            reloadMessage = TextUtils.concat(reloadMessage, Tuils.NEWLINE, c.text())
        }
        startMain.putExtra(Reloadable.MESSAGE, reloadMessage)
        startActivity(startMain)
    }

    private val `in`: Inputable = object : Inputable {
        override fun `in`(s: String?) {
            if (ui != null) ui!!.setInput(s as java.lang.String?)
        }

        override fun changeHint(s: String?) {
            runOnUiThread(Runnable { ui!!.setHint(s as java.lang.String?) })
        }

        override fun resetHint() {
            runOnUiThread(Runnable { ui!!.resetHint() })
        }
    }

    private val out: Outputable = object : Outputable {
        private val DELAY = 500

        var textColor: Queue<SimpleMutableEntry<CharSequence?, Int?>?>? =
            LinkedList<SimpleMutableEntry<CharSequence?, Int?>?>()
        var textCategory: Queue<SimpleMutableEntry<CharSequence?, Int?>?>? =
            LinkedList<SimpleMutableEntry<CharSequence?, Int?>?>()

        var charged: Boolean = false
        var handler: Handler? = Handler()

        var r: Runnable? = object : Runnable {
            override fun run() {
                if (ui == null) {
                    handler!!.postDelayed(this, DELAY.toLong())
                    return
                }

                var sm: SimpleMutableEntry<CharSequence?, Int?>?
                while ((textCategory!!.poll().also { sm = it }) != null) {
                    ui!!.setOutput(sm!!.key, sm.value!!)
                }

                while ((textColor!!.poll().also { sm = it }) != null) {
                    ui!!.setOutput(sm!!.value!!, sm.key)
                }

                textCategory = null
                textColor = null
                handler = null
                r = null
            }
        }

        override fun onOutput(output: CharSequence?) {
            if (ui != null) ui!!.setOutput(output, TerminalManager.CATEGORY_OUTPUT)
            else {
                textCategory!!.add(
                    SimpleMutableEntry<CharSequence?, Int?>(
                        output,
                        TerminalManager.CATEGORY_OUTPUT
                    )
                )

                if (!charged) {
                    charged = true
                    handler!!.postDelayed(r, DELAY.toLong())
                }
            }
        }

        override fun onOutput(output: CharSequence?, category: Int) {
            if (ui != null) ui!!.setOutput(output, category)
            else {
                textCategory!!.add(SimpleMutableEntry<CharSequence?, Int?>(output, category))

                if (!charged) {
                    charged = true
                    handler!!.postDelayed(r, DELAY.toLong())
                }
            }
        }

        override fun onOutput(color: Int, output: CharSequence?) {
            if (ui != null) ui!!.setOutput(color, output)
            else {
                textColor!!.add(SimpleMutableEntry<CharSequence?, Int?>(output, color))

                if (!charged) {
                    charged = true
                    handler!!.postDelayed(r, DELAY.toLong())
                }
            }
        }

        override fun dispose() {
            if (handler != null) handler!!.removeCallbacksAndMessages(null)
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)

        if (isFinishing()) {
            return
        }
        Log.i(TAG, "onCreate: ")
        checkAndRequestStoragePermissions()
        finishOnCreate()
    }

    private fun setOrientation() {
        val requestedOrientation = XMLPrefsManager.getInt(Behavior.orientation)
        if (requestedOrientation >= 0 && requestedOrientation != 2) {
            val orientation = getResources().getConfiguration().orientation
            if (orientation != requestedOrientation) setRequestedOrientation(requestedOrientation)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED)
            }
        }
    }

    private fun finishOnCreate() {
        Thread.currentThread().setUncaughtExceptionHandler(CustomExceptionHandler())
        XMLPrefsManager.loadCommons(this)
        RegexManager(this@LauncherActivity)
        TimeManager(this)

        val filter = IntentFilter()
        filter.addAction(PrivateIOReceiver.ACTION_INPUT)
        filter.addAction(PrivateIOReceiver.ACTION_OUTPUT)
        filter.addAction(PrivateIOReceiver.ACTION_REPLY)

        privateIOReceiver = PrivateIOReceiver(this, out, `in`)
        LocalBroadcastManager.getInstance(getApplicationContext())
            .registerReceiver(privateIOReceiver!!, filter)

        val filter1 = IntentFilter()
        filter1.addAction(PublicIOReceiver.ACTION_CMD)
        filter1.addAction(PublicIOReceiver.ACTION_OUTPUT)

        publicIOReceiver = PublicIOReceiver()
        getApplicationContext().registerReceiver(publicIOReceiver, filter1)
        setOrientation()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !XMLPrefsManager.getBoolean(Ui.ignore_bar_color)) {
            val window = getWindow()
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.setStatusBarColor(XMLPrefsManager.getColor(Theme.statusbar_color))
            window.setNavigationBarColor(XMLPrefsManager.getColor(Theme.navigationbar_color))
        }

        backButtonEnabled = XMLPrefsManager.getBoolean(Behavior.back_button_enabled)
        val showNotification = XMLPrefsManager.getBoolean(Behavior.tui_notification)
        if (showNotification) {
            val keeperIntent = Intent(this, KeeperService::class.java)
            keeperIntent.putExtra(KeeperService.PATH_KEY, XMLPrefsManager.get(Behavior.home_path))
            startService(keeperIntent)
        }

        val fullscreen = XMLPrefsManager.getBoolean(Ui.fullscreen)
        if (fullscreen) {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }

        val useSystemWP = XMLPrefsManager.getBoolean(Ui.system_wallpaper)
        if (useSystemWP) {
            setTheme(R.style.Custom_SystemWP)
        } else {
            setTheme(R.style.Custom_Solid)
        }

        try {
            NotificationManager.create(this)
        } catch (e: Exception) {
            Tuils.toFile(e)
        }

        val notifications =
            XMLPrefsManager.getBoolean(Notifications.show_notifications) || XMLPrefsManager.get(
                Notifications.show_notifications
            ).equals("enabled", ignoreCase = true)
        if (notifications) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                try {
                    val notificationComponent = ComponentName(this, NotificationService::class.java)
                    val pm = getPackageManager()
                    pm.setComponentEnabledSetting(
                        notificationComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )

                    if (!Tuils.hasNotificationAccess(this)) {
                        val i = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                        if (i.resolveActivity(getPackageManager()) == null) {
                            Toast.makeText(this, R.string.no_notification_access, Toast.LENGTH_LONG)
                                .show()
                        } else {
                            startActivity(i)
                        }
                    }

                    val monitor = Intent(this, NotificationMonitorService::class.java)
                    startService(monitor)

                    val notificationIntent = Intent(this, NotificationService::class.java)
                    startService(notificationIntent)
                } catch (er: NoClassDefFoundError) {
                    val intent = Intent(PrivateIOReceiver.ACTION_OUTPUT)
                    intent.putExtra(
                        PrivateIOReceiver.TEXT,
                        getString(R.string.output_notification_error) + Tuils.SPACE + er.toString()
                    )
                }
            } else {
                Tuils.sendOutput(Color.RED, this, R.string.notification_low_api)
            }
        }

        LongClickableSpan.longPressVibrateDuration =
            XMLPrefsManager.getInt(Behavior.long_click_vibration_duration)

        openKeyboardOnStart = XMLPrefsManager.getBoolean(Behavior.auto_show_keyboard)
        if (!openKeyboardOnStart) {
            this.getWindow()
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }

        setContentView(R.layout.base_view)

        if (XMLPrefsManager.getBoolean(Ui.show_restart_message)) {
            val s = getIntent().getCharSequenceExtra(Reloadable.MESSAGE)
            if (s != null) out.onOutput(
                Tuils.span(
                    s,
                    XMLPrefsManager.getColor(Theme.restart_message_color)
                )
            )
        }

        categories = HashSet<ReloadMessageCategory>()

        main = MainManager(this)

        val mainView = findViewById<View?>(R.id.mainview) as ViewGroup

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !XMLPrefsManager.getBoolean(Ui.ignore_bar_color) && !XMLPrefsManager.getBoolean(
                Ui.statusbar_light_icons
            )
        ) {
            mainView.setSystemUiVisibility(mainView.getSystemUiVisibility() or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR)
        }

        ui = UIManager(this, mainView, main!!.getMainPack(), canApplyTheme, main!!.executer())

        main!!.setRedirectionListener(ui!!.buildRedirectionListener())
        ui!!.pack = main!!.getMainPack()

        `in`.`in`(Tuils.EMPTYSTRING)
        ui!!.focusTerminal()

        if (fullscreen) Assist.assistActivity(this)

        System.gc()
    }

    override fun onStart() {
        super.onStart()

        if (ui != null) ui!!.onStart(openKeyboardOnStart)
    }

    override fun onRestart() {
        super.onRestart()

        LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(
            Intent(
                UIManager.ACTION_UPDATE_SUGGESTIONS
            )
        )
    }

    override fun onPause() {
        super.onPause()

        if (ui != null && main != null) {
            ui!!.pause()
            main!!.dispose()
        }
    }

    private var disposed = false
    private fun dispose() {
        if (disposed) return

        try {
            LocalBroadcastManager.getInstance(this.getApplicationContext())
                .unregisterReceiver(privateIOReceiver!!)
            getApplicationContext().unregisterReceiver(publicIOReceiver)
        } catch (e: Exception) {
            Tuils.log(e)
        }

        try {
            stopService(Intent(this, NotificationMonitorService::class.java))
        } catch (e: NoClassDefFoundError) {
            Tuils.log(e)
        } catch (e: Exception) {
            Tuils.log(e)
        }

        try {
            stopService(Intent(this, KeeperService::class.java))
        } catch (e: NoClassDefFoundError) {
            Tuils.log(e)
        } catch (e: Exception) {
            Tuils.log(e)
        }

        try {
            val notificationIntent = Intent(this, NotificationService::class.java)
            notificationIntent.putExtra(NotificationService.DESTROY, true)
            startService(notificationIntent)
        } catch (e: Throwable) {
            Tuils.log(e)
        }

        overridePendingTransition(0, 0)

        if (main != null) main!!.destroy()
        if (ui != null) ui!!.dispose()

        XMLPrefsManager.dispose()
        if (RegexManager.instance != null) {
            RegexManager.instance.dispose()
        }
        TimeManager.instance.dispose()

        disposed = true
    }

    override fun onDestroy() {
        super.onDestroy()
        dispose()
    }

    override fun onBackPressed() {
        if (backButtonEnabled && main != null) {
            ui!!.onBackPressed()
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK) return super.onKeyLongPress(keyCode, event)

        if (main != null) main!!.onLongBack()
        return true
    }

    override fun reload() {
        runOnUiThread(stopActivity)
    }

    override fun addMessage(header: String?, message: String?) {
        for (cs in categories!!) {
            Tuils.log(cs.header, header)
            if (cs.header == header) {
                cs.lines.add(message)
                return
            }
        }

        val c = ReloadMessageCategory(header)
        if (message != null) c.lines.add(message)
        categories!!.add(c)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && ui != null) {
            ui!!.focusTerminal()
        }
    }

    var suggestion: Suggestion? = null
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        suggestion = v.getTag(R.id.suggestion_id) as Suggestion?

        if (suggestion!!.type == Suggestion.TYPE_CONTACT) {
            val contact = suggestion!!.`object` as Contact

            menu.setHeaderTitle(contact.name)
            for (count in contact.numbers.indices) {
                menu.add(0, count, count, contact.numbers.get(count))
            }
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (suggestion != null) {
            if (suggestion!!.type == Suggestion.TYPE_CONTACT) {
                val contact = suggestion!!.`object` as Contact
                contact.setSelectedNumber(item.getItemId())

                Tuils.sendInput(this, suggestion!!.getText())

                return true
            }
        }

        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == TUIXT_REQUEST && resultCode != 0) {
            if (resultCode == TuixtActivity.BACK_PRESSED) {
                Tuils.sendOutput(this, R.string.tuixt_back_pressed)
            } else {
                Tuils.sendOutput(this, data.getStringExtra(TuixtActivity.ERROR_KEY))
            }
        }
    }

    /**
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (permissions!!.size > 0 && permissions[0] == Manifest.permission.READ_CONTACTS && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            LocalBroadcastManager.getInstance(this.getApplicationContext())
                .sendBroadcast(Intent(ContactManager.ACTION_REFRESH))
        }

        try {
            when (requestCode) {

            }
        } catch (e: Exception) {
        }
    }
    */

    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Permissions are granted at install time for API < 23
            Log.d(TAG, "Running on API < 23. Permissions granted at install time.")
            return
        }
        val allPermissionsGranted = REQUIRED_STORAGE_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allPermissionsGranted) {
            Log.d(TAG, "Running on API >= 23. Permissions already granted.")
        } else {
            Log.d(TAG, "Running on API >= 23. Requesting permissions.")
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_STORAGE_PERMISSIONS,
                STORAGE_PERMISSION
            )
        }
    }

    public fun checkAndRequestConnectivityPermissions() {
        val allPermissionsGranted = REQUIRED_CONNECTIVITY_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allPermissionsGranted) {
            Log.d(TAG, "All connectivity permissions granted.")
        } else {
            Log.d(TAG, "Requesting permissions.")
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_CONNECTIVITY_PERMISSIONS,
                CONNECTIVITY_PERMISSION
            )
        }


    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            // All requested permissions have been granted
            val message = "Permissions ${permissions.contentToString()} granted by user."
            Log.d(TAG, message)
            onPermissionGranted(requestCode)
        } else {
            // At least one permission was denied
            val message = "Permissions ${permissions.contentToString()} denied by user."
            Log.d(TAG, message)
            handlePermissionDenied(requestCode)
        }
    }

    private fun onPermissionGranted(requestCode: Int) {
        when (requestCode) {
            LOCATION_REQUEST_PERMISSION -> {
                val i = Intent(TuiLocationManager.ACTION_GOT_PERMISSION)
                i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, PackageManager.PERMISSION_GRANTED)
                LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(i)
            }
            STORAGE_PERMISSION -> {
                Log.d(TAG, "Storage permission granted, nothing more to do")
            }
            CONNECTIVITY_PERMISSION -> {
                Log.d(TAG, "Connectivity permissions granted, nothing more to do")
            }
            COMMAND_REQUEST_PERMISSION -> {
                val info = main!!.getMainPack()
                main!!.onCommand(info.lastCommand, null as String?, false)
            }
            COMMAND_SUGGESTION_REQUEST_PERMISSION -> {
                ui!!.setOutput(
                    getString(R.string.output_nopermissions),
                    TerminalManager.CATEGORY_OUTPUT
                )
            }
        }
    }

    private fun handlePermissionDenied(requestCode: Int) {
        when (requestCode) {
            STORAGE_PERMISSION -> {
                // Check if the user has permanently denied the permission (i.e., checked "Don't ask again")
                // shouldShowRequestPermissionRationale returns false if the user has permanently denied or it's the first time asking
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    val permission_rationale = "This app needs storage permission to save and load files. Please grant it."
                    showPermissionRationaleDialog(permission_rationale, REQUIRED_STORAGE_PERMISSIONS, STORAGE_PERMISSION)
                } else {
                    val msg = "Storage permission was permanently denied. You need to go to app settings to grant it manually for this feature to work."
                    val limited_fn_msg = "Storage permission denied. Functionality limited."
                    showSettingsDialog(msg, limited_fn_msg)
                }
            }
            LOCATION_REQUEST_PERMISSION -> {
                // TODO: jnduli

                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    val permission_rationale = "This app needs location permission to display location info in the UI. Please grant it."
                    showPermissionRationaleDialog(permission_rationale, REQUIRED_STORAGE_PERMISSIONS, STORAGE_PERMISSION)
                } else {
                    val msg = "Location permission was permanently denied. You need to go to app settings to grant it manually for this feature to work."
                    val limited_fn_msg = "Location permission denied. Functionality limited."
                    showSettingsDialog(msg, limited_fn_msg)
                }
            }
            CONNECTIVITY_PERMISSION -> {

                val should_show_rationale = REQUIRED_CONNECTIVITY_PERMISSIONS.any {
                    ActivityCompat.shouldShowRequestPermissionRationale(this, it)
                }
                if (should_show_rationale == true) {
                    val permission_rationale = "This app needs connectivity permission to display this info in the UI. Please grant it."
                    showPermissionRationaleDialog(permission_rationale, REQUIRED_CONNECTIVITY_PERMISSIONS, CONNECTIVITY_PERMISSION)
                } else {
                    val msg = "Connectivity  permission was permanently denied. You need to go to app settings to grant it manually for this feature to work."
                    val limited_fn_msg = "Connectivity permission denied. Functionality limited."
                    showSettingsDialog(msg, limited_fn_msg)
                }
            }
            COMMAND_REQUEST_PERMISSION -> {
                ui!!.setOutput(
                    getString(R.string.output_nopermissions),
                    TerminalManager.CATEGORY_OUTPUT
                )
                main!!.sendPermissionNotGrantedWarning()
            }
        }
    }

    private fun showPermissionRationaleDialog(permission_rationale: String, permissions: Array<out String>, requestCode: Int) {
        AlertDialog.Builder(this)
            .setTitle("Permission Needed")
            .setMessage(permission_rationale)
            .setPositiveButton("Grant") { dialog, _ ->
                dialog.dismiss()
                // Request permissions again
                ActivityCompat.requestPermissions(
                    this,
                    permissions,
                    requestCode,
                )
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Storage permission denied. Functionality limited.", Toast.LENGTH_LONG).show()
            }
            .create()
            .show()
    }

    private fun showSettingsDialog(rationale_msg: String, limited_fn_msg: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Permanently Denied")
            .setMessage(rationale_msg)
            .setPositiveButton("Go to Settings") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, limited_fn_msg, Toast.LENGTH_LONG).show()
            }
            .create()
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val cmd = intent.getStringExtra(PrivateIOReceiver.TEXT)
        if (cmd != null) {
            val i = Intent(MainManager.ACTION_EXEC)
            i.putExtra(MainManager.CMD_COUNT, MainManager.commandCount)
            i.putExtra(MainManager.CMD, cmd)
            i.putExtra(MainManager.NEED_WRITE_INPUT, true)
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(i)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
    }

    companion object {
        private const val TAG = "LauncherActivity"

        const val COMMAND_REQUEST_PERMISSION: Int = 10
        const val STORAGE_PERMISSION: Int = 11
        const val COMMAND_SUGGESTION_REQUEST_PERMISSION: Int = 12
        const val LOCATION_REQUEST_PERMISSION: Int = 13
        const val CONNECTIVITY_PERMISSION: Int = 14

        const val TUIXT_REQUEST: Int = 10

        val REQUIRED_CONNECTIVITY_PERMISSIONS = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
        )

    }
}
