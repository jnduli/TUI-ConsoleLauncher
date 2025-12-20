package ohi.andre.consolelauncher

import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.support.v4.content.LocalBroadcastManager
import android.support.v4.view.GestureDetectorCompat
import android.text.SpannableString
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.OnDoubleTapListener
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.View.OnTouchListener
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.NotesManager
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.TuiLocationManager
import ohi.andre.consolelauncher.managers.suggestions.SuggestionTextWatcher
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.AllowEqualsSequence
import ohi.andre.consolelauncher.tuils.NetworkUtils
import ohi.andre.consolelauncher.tuils.OutlineTextView
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import ohi.andre.consolelauncher.tuils.interfaces.OnBatteryUpdate
import ohi.andre.consolelauncher.tuils.interfaces.OnRedirectionListener
import ohi.andre.consolelauncher.tuils.interfaces.OnTextChanged
import ohi.andre.consolelauncher.tuils.stuff.PolicyReceiver
import java.io.File
import java.io.FileOutputStream
import java.lang.String
import java.lang.reflect.Method
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.Array
import kotlin.Boolean
import kotlin.BooleanArray
import kotlin.CharSequence
import kotlin.Double
import kotlin.Exception
import kotlin.Float
import kotlin.FloatArray
import kotlin.Int
import kotlin.IntArray
import kotlin.Long
import kotlin.LongArray
import kotlin.arrayOf
import kotlin.arrayOfNulls
import kotlin.booleanArrayOf
import kotlin.collections.ArrayList
import kotlin.collections.MutableList
import kotlin.math.min
import kotlin.plus
import kotlin.toString

class UIManager(
    context: Context,
    rootView: ViewGroup,
    mainPack: MainPack?,
    canApplyTheme: Boolean,
    executer: CommandExecuter?
) : OnTouchListener {
    public enum class Label {
        ram,
        device,
        time,
        battery,
        storage,
        network,
        notes,
        weather,
        unlock
    }

    private val RAM_DELAY = 3000
    private val TIME_DELAY = 1000
    private val STORAGE_DELAY = 60 * 1000

    public lateinit var mContext: Context

    private var handler = Handler()

    private var policy: DevicePolicyManager?
    private var component: ComponentName?
    private var gestureDetector: GestureDetectorCompat? = null

    var preferences: SharedPreferences

    private val imm: InputMethodManager
    private var mTerminalAdapter: TerminalManager?

    var mediumPercentage: Int = 0
    var lowPercentage: Int = 0
    var batteryFormat: String? = null

    var hideToolbarNoInput: Boolean = false
    var toolbarView: View? = null

    //    never access this directly, use getLabelView
    // TODO: change this to a map to enable easier modification and access
    val mapLabelViews = loadTextViews(rootView)

    private fun getLabelView(l: Label): TextView? {
        val dataLabel = mapLabelViews.get(l)
        return dataLabel?.textView
    }

    public fun getLabelSize(l: Label): Int {
        val tv = getLabelView(l)
        if (tv == null) {
            return 0
        }
        return tv.text.length
    }

    private var notesMaxLines = 0
    private var notesManager: NotesManager? = null

    private inner class NotesRunnable : Runnable {
        var updateTime: Int = 2000

        override fun run() {
            if (notesManager != null) {
                if (notesManager!!.hasChanged) {
                    this@UIManager.updateText(
                        Label.notes,
                        notesManager!!.getNotes()
                    )
                }

                handler.postDelayed(this, updateTime.toLong())
            }
        }
    }

    private var batteryUpdate: BatteryUpdate? = null

    private inner class BatteryUpdate : OnBatteryUpdate {
        //        %(charging:not charging)
        //        final Pattern optionalCharging = Pattern.compile("%\\(([^\\/]*)\\/([^)]*)\\)", Pattern.CASE_INSENSITIVE);
        var optionalCharging: Pattern? = null
        val value: Pattern = Pattern.compile("%v", Pattern.LITERAL or Pattern.CASE_INSENSITIVE)

        var manyStatus: Boolean = false
        var loaded: Boolean = false
        var colorHigh: Int = 0
        var colorMedium: Int = 0
        var colorLow: Int = 0

        var charging: Boolean = false
        var last: Float = -1f

        override fun update(p: Float) {
            var p = p
            if (batteryFormat == null) {
                batteryFormat = XMLPrefsManager.get(Behavior.battery_format) as String?

                val intent =
                    mContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (intent == null) charging = false
                else {
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    charging =
                        plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB
                }

                val optionalSeparator =
                    "\\" + XMLPrefsManager.get(Behavior.optional_values_separator)
                val optional =
                    "%\\(([^" + optionalSeparator + "]*)" + optionalSeparator + "([^)]*)\\)"
                optionalCharging = Pattern.compile(optional, Pattern.CASE_INSENSITIVE)
            }

            if (p == -1f) p = last
            last = p

            if (!loaded) {
                loaded = true

                manyStatus = XMLPrefsManager.getBoolean(Ui.enable_battery_status)
                colorHigh = XMLPrefsManager.getColor(Theme.battery_color_high)
                colorMedium = XMLPrefsManager.getColor(Theme.battery_color_medium)
                colorLow = XMLPrefsManager.getColor(Theme.battery_color_low)
            }

            val percentage = p.toInt()

            val color: Int

            if (manyStatus) {
                if (percentage > mediumPercentage) color = colorHigh
                else if (percentage > lowPercentage) color = colorMedium
                else color = colorLow
            } else {
                color = colorHigh
            }

            var cp: String? = batteryFormat

            val m = optionalCharging?.matcher(cp)
            if (m != null) {
                while (m.find()) {
                    cp = cp?.replace(
                        m.group(0),
                        if (m.groupCount() == 2) m.group(if (charging) 1 else 2) else Tuils.EMPTYSTRING
                    ) as String?
                }
            }

            cp = value.matcher(cp).replaceAll(percentage.toString()) as String?
            cp = Tuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE) as String?

            this@UIManager.updateText(
                Label.battery,
                Tuils.span(cp, color)
            )
        }

        override fun onCharging() {
            charging = true
            update(-1f)
        }

        override fun onNotCharging() {
            charging = false
            update(-1f)
        }
    }

    private var storageRunnable: StorageRunnable? = null


    public var activityManager: ActivityManager? = null

    private var ramRunnable: RamRunnable? = null

    private var networkRunnable: NetworkRunnable? = null


    private var weatherDelay = 0

    private var lastLatitude = 0.0
    private var lastLongitude = 0.0
    private var location: String? = null
    private var fixedLocation = false

    private var weatherPerformedStartupRun = false
    private var weatherRunnable: WeatherRunnable? = null
    private var weatherColor = 0
    var showWeatherUpdate: Boolean = false

    data class LabelView(val textView: TextView, val size: Int, val color: Int, val show: Boolean)

    private fun loadTextViews(rootView: ViewGroup): Map<Label, LabelView?> {
       return  mapOf(
            Label.time to LabelView(
                rootView.findViewById<View?>(R.id.tv0) as TextView,
                XMLPrefsManager.getInt(Ui.time_size),
                XMLPrefsManager.getColor(Theme.storage_color),
                XMLPrefsManager.getBoolean(Ui.show_time),
            ),
           Label.ram to LabelView(
               rootView.findViewById<View?>(R.id.tv1) as TextView,
               XMLPrefsManager.getInt(Ui.ram_size),
               XMLPrefsManager.getColor(Theme.ram_color),
               XMLPrefsManager.getBoolean(Ui.show_ram),
           ),
           Label.battery to LabelView(
                   rootView.findViewById<View?>(R.id.tv2) as TextView,
                   XMLPrefsManager.getInt(Ui.battery_size),
                    // TODO: needs to be variable and set by the battery method
                   XMLPrefsManager.getColor(Theme.battery_color_medium),
                   XMLPrefsManager.getBoolean(Ui.show_battery),
           ),
           Label.storage to LabelView(
               rootView.findViewById<View?>(R.id.tv3) as TextView,
               XMLPrefsManager.getInt(Ui.storage_size),
               XMLPrefsManager.getColor(Theme.storage_color),
               XMLPrefsManager.getBoolean(Ui.show_storage_info),
           ),
           Label.network to LabelView(
               rootView.findViewById<View?>(R.id.tv4) as TextView,
               XMLPrefsManager.getInt(Ui.network_size),
               XMLPrefsManager.getColor(Theme.network_info_color),
               XMLPrefsManager.getBoolean(Ui.show_network_info),
           ),
           Label.notes to LabelView(
               rootView.findViewById<View?>(R.id.tv5) as TextView,
               XMLPrefsManager.getInt(Ui.notes_size),
               XMLPrefsManager.getColor(Theme.notes_color),
                   XMLPrefsManager.getBoolean(Ui.show_notes),
           ),
           Label.device to LabelView(
               rootView.findViewById<View?>(R.id.tv6) as TextView,
               XMLPrefsManager.getInt(Ui.device_size),
               XMLPrefsManager.getColor(Theme.device_color),
               XMLPrefsManager.getBoolean(Ui.show_device_name),
           ),
           Label.weather to LabelView(
               rootView.findViewById<View?>(R.id.tv7) as TextView,
               XMLPrefsManager.getInt(Ui.weather_size),
               XMLPrefsManager.getColor(Theme.weather_color),
               XMLPrefsManager.getBoolean(Ui.show_weather),
           ),
           Label.unlock to LabelView(
               rootView.findViewById<View?>(R.id.tv8) as TextView,
               XMLPrefsManager.getInt(Ui.unlock_size),
               XMLPrefsManager.getColor(Theme.unlock_counter_color),
               XMLPrefsManager.getBoolean(Ui.show_unlock_counter),
           ),
        )
    }

    private inner class WeatherRunnable : Runnable {
        var key: String? = null
        var url: String? = null

        init {
            if (XMLPrefsManager.wasChanged(Behavior.weather_key, false)) {
                weatherDelay = XMLPrefsManager.getInt(Behavior.weather_update_time)
                key = XMLPrefsManager.get(Behavior.weather_key) as String?
            } else {
                key = Behavior.weather_key.defaultValue() as String?
                weatherDelay = 60 * 60
            }
            weatherDelay *= 1000

            var where: String? = XMLPrefsManager.get(Behavior.weather_location) as String?
            if (where == null || where.length == 0 || (!Tuils.isNumber(where as kotlin.String?) && !where.contains(","))) {
                val l = TuiLocationManager.instance(mContext)
                l.add(ACTION_WEATHER_GOT_LOCATION)
            } else {
                fixedLocation = true

                if (where.contains(",")) {
                    val split: Array<kotlin.String> =
                        where.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    where = ("lat=" + split[0] + "&lon=" + split[1]) as String?
                } else {
                    where = ("id=" + where) as String?
                }

                setUrlWithWhere(where)
            }
        }

        override fun run() {
            weatherPerformedStartupRun = true
            if (!fixedLocation) setUrlWithLatLon(lastLatitude, lastLongitude)
            send()
            handler.postDelayed(this, weatherDelay.toLong())
        }

        fun send() {
            if (url == null) return

            val i = Intent(HTMLExtractManager.ACTION_WEATHER)
            i.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, url as CharSequence?)
            i.putExtra(HTMLExtractManager.BROADCAST_COUNT, HTMLExtractManager.broadcastCount)
            LocalBroadcastManager.getInstance(mContext.getApplicationContext()).sendBroadcast(i)
        }

        fun setUrlWithWhere(where: String?) {
            url = (
                "http://api.openweathermap.org/data/2.5/weather?" + where + "&appid=" + key + "&units=" + XMLPrefsManager.get(
                    Behavior.weather_temperature_measure
                )) as String?
        }

        fun setUrlWithLatLon(latitude: Double, longitude: Double) {
            url = (
                "http://api.openweathermap.org/data/2.5/weather?" + "lat=" + latitude + "&lon=" + longitude + "&appid=" + key + "&units=" + XMLPrefsManager.get(
                    Behavior.weather_temperature_measure
                )) as String?
        }
    }

    public fun updateText(l: Label, s: CharSequence) {
        val dataLabel = mapLabelViews.get(l)
        if (dataLabel?.show == false ||  s.length <= 0) {
            dataLabel?.textView?.setVisibility(View.GONE)
            return
        }
        val color = dataLabel?.color?: Color.RED
        val coloredSpan = Tuils.span(s, color)
        dataLabel?.textView?.setVisibility(View.VISIBLE)
        dataLabel?.textView?.setText(coloredSpan)
    }

    private var suggestionsManager: SuggestionsManager? = null

    private val terminalView: TextView

    private var doubleTapCmd: String?
    private var lockOnDbTap: Boolean

    private val receiver: BroadcastReceiver

    @kotlin.jvm.JvmField
    var pack: MainPack? = null

    private val clearOnLock = XMLPrefsManager.getBoolean(Behavior.clear_on_lock)

    fun dispose() {
        handler.removeCallbacksAndMessages(null)
        suggestionsManager?.dispose()
        notesManager?.dispose(mContext)

        LocalBroadcastManager.getInstance(mContext.getApplicationContext())
            .unregisterReceiver(receiver)
        Tuils.unregisterBatteryReceiver(mContext)

        Tuils.cancelFont()

        unregisterLockReceiver()
    }

    fun openKeyboard() {
        mTerminalAdapter?.requestInputFocus()
        imm.showSoftInput(mTerminalAdapter?.getInputView(), InputMethodManager.SHOW_IMPLICIT)
        //        mTerminalAdapter.scrollToEnd();
    }

    fun closeKeyboard() {
        imm.hideSoftInputFromWindow(mTerminalAdapter?.getInputWindowToken(), 0)
    }

    fun onStart(openKeyboardOnStart: Boolean) {
        if (openKeyboardOnStart) openKeyboard()
    }

    fun setInput(s: String?) {
        if (s == null) return

        mTerminalAdapter?.setInput(s as kotlin.String?)
        mTerminalAdapter?.focusInputEnd()
    }

    fun setHint(hint: String?) {
        mTerminalAdapter?.setHint(hint as kotlin.String?)
    }

    fun resetHint() {
        mTerminalAdapter?.setDefaultHint()
    }

    fun setOutput(s: CharSequence?, category: Int) {
        mTerminalAdapter?.setOutput(s, category)
    }

    fun setOutput(color: Int, output: CharSequence?) {
        mTerminalAdapter?.setOutput(color, output)
    }

    fun disableSuggestions() {
        suggestionsManager?.disable()
    }

    fun enableSuggestions() {
        suggestionsManager?.enable()
    }

    fun onBackPressed() {
        mTerminalAdapter?.onBackPressed()
    }

    fun focusTerminal() {
        mTerminalAdapter?.requestInputFocus()
    }

    fun pause() {
        closeKeyboard()
    }

    override fun onTouch(v: View, event: MotionEvent?): Boolean {
        gestureDetector?.onTouchEvent(event)
        return v.onTouchEvent(event)
    }

    fun buildRedirectionListener(): OnRedirectionListener {
        return object : OnRedirectionListener {
            override fun onRedirectionRequest(cmd: RedirectCommand) {
                (mContext as Activity).runOnUiThread(Runnable {
                    mTerminalAdapter?.setHint(mContext.getString(cmd.getHint()))
                    disableSuggestions()
                })
            }

            override fun onRedirectionEnd(cmd: RedirectCommand?) {
                (mContext as Activity).runOnUiThread(Runnable {
                    mTerminalAdapter?.setDefaultHint()
                    enableSuggestions()
                })
            }
        }
    }

    private var lockReceiver: BroadcastReceiver? = null
    private fun registerLockReceiver() {
        if (lockReceiver != null) return

        val theFilter = IntentFilter()

        theFilter.addAction(Intent.ACTION_SCREEN_ON)
        theFilter.addAction(Intent.ACTION_SCREEN_OFF)
        theFilter.addAction(Intent.ACTION_USER_PRESENT)

        lockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val strAction = intent.getAction()

                val myKM = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (strAction == Intent.ACTION_USER_PRESENT || strAction == Intent.ACTION_SCREEN_OFF || strAction == Intent.ACTION_SCREEN_ON) if (myKM.inKeyguardRestrictedInputMode()) onLock()
                else onUnlock()
            }
        }

        mContext.getApplicationContext().registerReceiver(lockReceiver, theFilter)
    }

    private fun unregisterLockReceiver() {
        if (lockReceiver != null) mContext.getApplicationContext().unregisterReceiver(lockReceiver)
    }

    private fun onLock() {
        if (clearOnLock) {
            mTerminalAdapter?.clear()
        }
    }

    private val A_DAY = (1000 * 60 * 60 * 24).toLong()

    private var unlockColor = 0
    private var unlockTimeOrder = 0

    private var unlockTimes = 0
    private var unlockHour = 0
    private var unlockMinute = 0
    private val cycleDuration = A_DAY.toInt()
    private var lastUnlockTime: Long = -1
    private var nextUnlockCycleRestart: Long = 0
    private var unlockFormat: String? = null
    private var notAvailableText: String? = null
    private var unlockTimeDivider: String? = null

    private val UP_DOWN = 1

    //    last unlocks are stored here in this way
    //    0 - the first
    //    1 - the second
    //    2 - ...
    private var lastUnlocks: LongArray? = null

    private fun onUnlock() {
        if (System.currentTimeMillis() - lastUnlockTime < 1000 || lastUnlocks == null) return
        lastUnlockTime = System.currentTimeMillis()

        unlockTimes++

        System.arraycopy(lastUnlocks!!, 0, lastUnlocks, 1, lastUnlocks!!.size - 1)
        lastUnlocks!![0] = lastUnlockTime

        preferences.edit()
            .putInt(UNLOCK_KEY, unlockTimes)
            .apply()

        invalidateUnlockText()
    }

    val UNLOCK_RUNNABLE_DELAY: Int = cycleDuration / 24

    //    this invalidates the text and checks the time values
    var unlockTimeRunnable: Runnable = object : Runnable {
        override fun run() {
//            Tuils.log("run");
            var delay = nextUnlockCycleRestart - System.currentTimeMillis()
            //            Tuils.log("nucr", nextUnlockCycleRestart);
//            Tuils.log("now", System.currentTimeMillis());
//            Tuils.log("delay", delay);
            if (delay <= 0) {
                unlockTimes = 0

                if (lastUnlocks != null) {
                    for (c in lastUnlocks!!.indices) {
                        lastUnlocks!![c] = -1
                    }
                }

                val now = Calendar.getInstance()

                //                Tuils.log("nw", now.toString());
                val hour = now.get(Calendar.HOUR_OF_DAY)
                val minute = now.get(Calendar.MINUTE)
                if (unlockHour < hour || (unlockHour == hour && unlockMinute <= minute)) {
                    now.set(Calendar.DAY_OF_YEAR, now.get(Calendar.DAY_OF_YEAR) + 1)
                }
                val nextRestart = now
                nextRestart.set(Calendar.HOUR_OF_DAY, unlockHour)
                nextRestart.set(Calendar.MINUTE, unlockMinute)
                nextRestart.set(Calendar.SECOND, 0)

                //                Tuils.log("nr", nextRestart.toString());
                nextUnlockCycleRestart = nextRestart.getTimeInMillis()

                //                Tuils.log("new setted", nextUnlockCycleRestart);
                preferences.edit()
                    .putLong(NEXT_UNLOCK_CYCLE_RESTART, nextUnlockCycleRestart)
                    .putInt(UNLOCK_KEY, 0)
                    .apply()

                delay = nextUnlockCycleRestart - System.currentTimeMillis()
                if (delay < 0) delay = 0
            }

            invalidateUnlockText()

            delay = min(delay, UNLOCK_RUNNABLE_DELAY.toLong())
            //            Tuils.log("with delay", delay);
            handler.postDelayed(this, delay)
        }
    }

    var unlockCount: Pattern = Pattern.compile("%c", Pattern.CASE_INSENSITIVE)
    var advancement: Pattern = Pattern.compile("%a(\\d+)(.)")

    //    Pattern timePattern = Pattern.compile("(%t\\d*)(?:\\((?:(\\d+)([^\\)]*))\\)|\\((?:([^\\)]*)(\\d+))\\))?");
    var timePattern: Pattern = Pattern.compile("(%t\\d*)(?:\\(([^\\)]*)\\))?(\\d+)?")
    var indexPattern: Pattern = Pattern.compile("%i", Pattern.CASE_INSENSITIVE)
    var whenPattern  = "%w"

    init {
        val filter = IntentFilter()
        filter.addAction(ACTION_UPDATE_SUGGESTIONS)
        filter.addAction(ACTION_UPDATE_HINT)
        filter.addAction(ACTION_ROOT)
        filter.addAction(ACTION_NOROOT)
        //        filter.addAction(ACTION_CLEAR_SUGGESTIONS);
        filter.addAction(ACTION_LOGTOFILE)
        filter.addAction(ACTION_CLEAR)
        filter.addAction(ACTION_WEATHER)
        filter.addAction(ACTION_WEATHER_GOT_LOCATION)
        filter.addAction(ACTION_WEATHER_DELAY)
        filter.addAction(ACTION_WEATHER_MANUAL_UPDATE)

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.getAction()

                if (action == ACTION_UPDATE_SUGGESTIONS) {
                    suggestionsManager?.requestSuggestion(Tuils.EMPTYSTRING)
                } else if (action == ACTION_UPDATE_HINT) {
                    mTerminalAdapter?.setDefaultHint()
                } else if (action == ACTION_ROOT) {
                    mTerminalAdapter?.onRoot()
                } else if (action == ACTION_NOROOT) {
                    mTerminalAdapter?.onStandard()
                    //                } else if(action.equals(ACTION_CLEAR_SUGGESTIONS)) {
//                    if(suggestionsManager != null) suggestionsManager.clear();
                } else if (action == ACTION_LOGTOFILE) {
                    val fileName = intent.getStringExtra(FILE_NAME)
                    if (fileName == null || fileName.contains(File.separator)) return

                    val file = File(Tuils.getFolder(context), fileName)
                    if (file.exists()) file.delete()

                    try {
                        file.createNewFile()

                        val fos = FileOutputStream(file)
                        fos.write(mTerminalAdapter?.getTerminalText()?.toByteArray())

                        Tuils.sendOutput(context, "Logged to " + file.getAbsolutePath())
                    } catch (e: Exception) {
                        Tuils.sendOutput(Color.RED, context, e.toString())
                    }
                } else if (action == ACTION_CLEAR) {
                    mTerminalAdapter?.clear()
                    suggestionsManager?.requestSuggestion(Tuils.EMPTYSTRING)
                } else if (action == ACTION_WEATHER) {
                    val c = Calendar.getInstance()

                    var s = intent.getCharSequenceExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                    if (s == null) s = intent.getStringExtra(XMLPrefsManager.VALUE_ATTRIBUTE)
                    if (s == null) return
                    s = Tuils.span(s, weatherColor)
                    updateText(Label.weather, s)

                    if (showWeatherUpdate) {
                        val message =
                            context.getString(R.string.weather_updated) + Tuils.SPACE + c.get(
                                Calendar.HOUR_OF_DAY
                            ) + "." + c.get(Calendar.MINUTE) + Tuils.SPACE + "(" + lastLatitude + ", " + lastLongitude + ")"
                        Tuils.sendOutput(context, message, TerminalManager.CATEGORY_OUTPUT)
                    }
                } else if (action == ACTION_WEATHER_GOT_LOCATION) {
//                    int result = intent.getIntExtra(XMLPrefsManager.VALUE_ATTRIBUTE, 0);
//                    if(result == PackageManager.PERMISSION_DENIED) {
//                        updateText(Label.weather, Tuils.span(context, context.getString(R.string.location_error), weatherColor, labelSizes[Label.weather.ordinal()]));
//                    } else handler.post(weatherRunnable);

                    if (intent.getBooleanExtra(TuiLocationManager.FAIL, false)) {
                        handler.removeCallbacks(weatherRunnable)
                        weatherRunnable = null

                        val s: CharSequence = Tuils.span(
                            context.getString(R.string.location_error),
                            weatherColor,
                        )

                        updateText(Label.weather, s)
                    } else {
                        lastLatitude = intent.getDoubleExtra(TuiLocationManager.LATITUDE, 0.0)
                        lastLongitude = intent.getDoubleExtra(TuiLocationManager.LONGITUDE, 0.0)

                        location = Tuils.locationName(context, lastLatitude, lastLongitude) as String?

                        if (!weatherPerformedStartupRun || XMLPrefsManager.wasChanged(
                                Behavior.weather_key,
                                false
                            )
                        ) {
                            handler.removeCallbacks(weatherRunnable)
                            handler.post(weatherRunnable)
                        }
                    }
                } else if (action == ACTION_WEATHER_DELAY) {
                    val c = Calendar.getInstance()
                    c.setTimeInMillis(System.currentTimeMillis() + 1000 * 10)

                    if (showWeatherUpdate) {
                        val message =
                            context.getString(R.string.weather_error) + Tuils.SPACE + c.get(
                                Calendar.HOUR_OF_DAY
                            ) + "." + c.get(Calendar.MINUTE)
                        Tuils.sendOutput(context, message, TerminalManager.CATEGORY_OUTPUT)
                    }

                    handler.removeCallbacks(weatherRunnable)
                    handler.postDelayed(weatherRunnable, (1000 * 60).toLong())
                } else if (action == ACTION_WEATHER_MANUAL_UPDATE) {
                    handler.removeCallbacks(weatherRunnable)
                    handler.post(weatherRunnable)
                }
            }
        }

        LocalBroadcastManager.getInstance(context.getApplicationContext())
            .registerReceiver(receiver, filter)

        policy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager?
        component = ComponentName(context, PolicyReceiver::class.java)

        mContext = context

        preferences = mContext.getSharedPreferences(PREFS_NAME, 0)


        imm = mContext.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        if (!XMLPrefsManager.getBoolean(Ui.system_wallpaper) || !canApplyTheme) {
            rootView.setBackgroundColor(XMLPrefsManager.getColor(Theme.bg_color))
        } else {
            rootView.setBackgroundColor(XMLPrefsManager.getColor(Theme.overlay_color))
        }

        //        scrolllllll
        if (XMLPrefsManager.getBoolean(Behavior.auto_scroll)) {
            rootView.getViewTreeObserver().addOnGlobalLayoutListener(OnGlobalLayoutListener {
                val heightDiff = rootView.getRootView().getHeight() - rootView.getHeight()
                if (heightDiff > Tuils.dpToPx(
                        context,
                        200
                    )
                ) { // if more than 200 dp, it's probably a keyboard...
                    mTerminalAdapter?.scrollToEnd()
                }
            })
        }


        lockOnDbTap = XMLPrefsManager.getBoolean(Behavior.double_tap_lock)
        doubleTapCmd = XMLPrefsManager.get(Behavior.double_tap_cmd) as String?
        if (!lockOnDbTap && doubleTapCmd == null) {
            policy = null
            component = null
            gestureDetector = null
        } else {
            gestureDetector =
                GestureDetectorCompat(mContext, object : GestureDetector.OnGestureListener {
                    override fun onDown(e: MotionEvent?): Boolean {
                        return false
                    }

                    override fun onShowPress(e: MotionEvent?) {}

                    override fun onSingleTapUp(e: MotionEvent?): Boolean {
                        return false
                    }

                    override fun onScroll(
                        e1: MotionEvent?,
                        e2: MotionEvent?,
                        distanceX: Float,
                        distanceY: Float
                    ): Boolean {
                        return false
                    }

                    override fun onLongPress(e: MotionEvent?) {}

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent?,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {
                        return false
                    }
                })

            gestureDetector?.setOnDoubleTapListener(object : OnDoubleTapListener {
                override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                    return false
                }

                override fun onDoubleTapEvent(e: MotionEvent?): Boolean {
                    return true
                }

                override fun onDoubleTap(e: MotionEvent?): Boolean {
                    if (doubleTapCmd != null && doubleTapCmd!!.length > 0) {
                        val input = mTerminalAdapter?.getInput()
                        mTerminalAdapter?.setInput(doubleTapCmd as kotlin.String?)
                        mTerminalAdapter?.simulateEnter()
                        mTerminalAdapter?.setInput(input)
                    }

                    if (lockOnDbTap) {
                        val admin = policy?.isAdminActive(component!!)

                        admin?.let {
                            if (!it) {
                                val i = Tuils.requestAdmin(
                                    component,
                                    mContext.getString(R.string.admin_permission)
                                )
                                mContext.startActivity(i)
                            } else {
                                policy!!.lockNow()
                            }
                        }
                    }

                    return true
                }
            })
        }

        val displayMargins: IntArray =
            getListOfIntValues(XMLPrefsManager.get(Ui.display_margin_mm), 4, 0)
        val metrics = mContext.getResources().getDisplayMetrics()
        rootView.setPadding(
            Tuils.mmToPx(metrics, displayMargins[0]),
            Tuils.mmToPx(metrics, displayMargins[1]),
            Tuils.mmToPx(metrics, displayMargins[2]),
            Tuils.mmToPx(metrics, displayMargins[3])
        )


        val statusLineAlignments: IntArray =
            getListOfIntValues(XMLPrefsManager.get(Ui.status_lines_alignment), 9, -1)

        val statusLinesBgRectColors: Array<kotlin.String?> = getListOfStringValues(
            XMLPrefsManager.get(
                Theme.status_lines_bgrectcolor
            ), 9, "#ff000000"
        )
        val otherBgRectColors = arrayOf<String?>(
            XMLPrefsManager.get(Theme.input_bgrectcolor) as String?,
            XMLPrefsManager.get(Theme.output_bgrectcolor) as String?,
            XMLPrefsManager.get(Theme.suggestions_bgrectcolor) as String?,
            XMLPrefsManager.get(Theme.toolbar_bgrectcolor) as String?
        )
        val bgRectColors =
            arrayOfNulls<String>(statusLinesBgRectColors.size + otherBgRectColors.size)
        System.arraycopy(statusLinesBgRectColors, 0, bgRectColors, 0, statusLinesBgRectColors.size)
        System.arraycopy(
            otherBgRectColors,
            0,
            bgRectColors,
            statusLinesBgRectColors.size,
            otherBgRectColors.size
        )

        val statusLineBgColors: Array<kotlin.String?> =
            getListOfStringValues(XMLPrefsManager.get(Theme.status_lines_bg), 9, "#00000000")
        val otherBgColors = arrayOf<String?>(
            XMLPrefsManager.get(Theme.input_bg) as String?,
            XMLPrefsManager.get(Theme.output_bg) as String?,
            XMLPrefsManager.get(Theme.suggestions_bg) as String?,
            XMLPrefsManager.get(Theme.toolbar_bg) as String?
        )
        val bgColors = arrayOfNulls<String>(statusLineBgColors.size + otherBgColors.size)
        System.arraycopy(statusLineBgColors, 0, bgColors, 0, statusLineBgColors.size)
        System.arraycopy(otherBgColors, 0, bgColors, statusLineBgColors.size, otherBgColors.size)

        val statusLineOutlineColors: Array<kotlin.String?> = getListOfStringValues(
            XMLPrefsManager.get(
                Theme.status_lines_shadow_color
            ), 9, "#00000000"
        )
        val otherOutlineColors = arrayOf<kotlin.String?>(
            XMLPrefsManager.get(Theme.input_shadow_color),
            XMLPrefsManager.get(Theme.output_shadow_color),
        )
        val outlineColors =
            arrayOfNulls<String>(statusLineOutlineColors.size + otherOutlineColors.size)
        System.arraycopy(statusLineOutlineColors, 0, outlineColors, 0, statusLineOutlineColors.size)
        System.arraycopy(otherOutlineColors, 0, outlineColors, 9, otherOutlineColors.size)

        val shadowXOffset: Int
        val shadowYOffset: Int
        val shadowRadius: Float
        val shadowParams: Array<kotlin.String?> =
            getListOfStringValues(XMLPrefsManager.get(Ui.shadow_params), 3, "0")
        shadowXOffset = shadowParams[0]?.toInt() ?: 0
        shadowYOffset = shadowParams[1]?.toInt() ?: 0
        shadowRadius = (shadowParams[2]?.toFloat() ?: 0.0) as Float

        val INPUT_BGCOLOR_INDEX = 9
        val OUTPUT_BGCOLOR_INDEX = 10
        val SUGGESTIONS_BGCOLOR_INDEX = 11
        val TOOLBAR_BGCOLOR_INDEX = 12

        val rectParams: Array<kotlin.String?> =
            getListOfStringValues(XMLPrefsManager.get(Ui.bgrect_params), 2, "0")
        val strokeWidth = rectParams[0]?.toInt() ?: 0
        val cornerRadius = rectParams[1]?.toInt() ?: 0

        val OUTPUT_MARGINS_INDEX = 1
        val INPUTAREA_MARGINS_INDEX = 2
        val INPUTFIELD_MARGINS_INDEX = 3
        val TOOLBAR_MARGINS_INDEX = 4
        val SUGGESTIONS_MARGINS_INDEX = 5

        val margins = Array<IntArray?>(6) { IntArray(4) }
        margins[0] = getListOfIntValues(XMLPrefsManager.get(Ui.status_lines_margins), 4, 0)
        margins[1] = getListOfIntValues(XMLPrefsManager.get(Ui.output_field_margins), 4, 0)
        margins[2] = getListOfIntValues(XMLPrefsManager.get(Ui.input_area_margins), 4, 0)
        margins[3] = getListOfIntValues(XMLPrefsManager.get(Ui.input_field_margins), 4, 0)
        margins[4] = getListOfIntValues(XMLPrefsManager.get(Ui.toolbar_margins), 4, 0)
        margins[5] = getListOfIntValues(XMLPrefsManager.get(Ui.suggestions_area_margin), 4, 0)



        for ((label, view) in  mapLabelViews) {
            val tv = view?.textView
            if (tv == null) {
                continue
            }
            tv.setOnTouchListener(this)
            tv.setTypeface(Tuils.getTypeface(context))
            if (view?.show == false) {
                val viewParent = tv.parent as LinearLayout
                viewParent.removeView(tv)
            }
            if (label != Label.notes) {
                tv.setVerticalScrollBarEnabled(false)
            }
            val strokeColor = XMLPrefsManager.get(Theme.output_bgrectcolor).toString()
            val bgColor = XMLPrefsManager.get(Theme.output_bg).toString()
            val spaces = getListOfIntValues(XMLPrefsManager.get(Ui.output_field_margins), 4, 0)
            Companion.applyBgRect(tv, strokeColor, bgColor, spaces, strokeWidth, cornerRadius )
            val outlineColor = XMLPrefsManager.get(Theme.output_shadow_color).toString()
            Companion.applyShadow(tv, outlineColor, shadowXOffset, shadowYOffset, shadowRadius)

            // val p = statusLineAlignments[ec]
            // if (p >= 0) labelViews[count]?.setGravity(if (p == 0) Gravity.CENTER_HORIZONTAL else Gravity.RIGHT)

            when (label) {
                Label.ram -> {
                    ramRunnable = RamRunnable(this, handler)
                    activityManager = context.getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
                    handler.post(ramRunnable)
                }
                Label.device -> {
                    val username = XMLPrefsManager.get(Ui.username)
                    var deviceName = XMLPrefsManager.get(Ui.deviceName)?: Build.DEVICE
                    val content = "$username: $deviceName"
                    val span = Tuils.span(content, XMLPrefsManager.getColor(Theme.device_color))
                    updateText(label, span)
                }
                Label.time -> {
                    var timeRunnable = TimeRunnable(this, handler)
                    handler.post(timeRunnable)
                }
                Label.battery -> {
                    batteryUpdate = BatteryUpdate()

                    mediumPercentage = XMLPrefsManager.getInt(Behavior.battery_medium)
                    lowPercentage = XMLPrefsManager.getInt(Behavior.battery_low)

                    Tuils.registerBatteryReceiver(context, batteryUpdate)
                }
                Label.storage -> {
                    storageRunnable = StorageRunnable(this, handler)
                    handler.post(storageRunnable)
                }
                Label.network -> {
                    networkRunnable = NetworkRunnable(this, handler)
                    handler.post(networkRunnable)
                }
                Label.notes -> {
                    notesManager = NotesManager(context, tv)
                    val notesRunnable = NotesRunnable()
                    handler.post(notesRunnable)
                    tv.setMovementMethod(LinkMovementMethod())
                    notesMaxLines = XMLPrefsManager.getInt(Ui.notes_max_lines)
                    if (notesMaxLines > 0) {
                        tv.setMaxLines(notesMaxLines)
                        tv.setEllipsize(TextUtils.TruncateAt.MARQUEE)
                        if (XMLPrefsManager.getBoolean( Ui.show_scroll_notes_message ) ) {
                            tv.getViewTreeObserver()
                                ?.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                                    var linesBefore: Int = Int.Companion.MIN_VALUE
                                    override fun onGlobalLayout() {
                                        if (tv.getLineCount() > notesMaxLines && linesBefore <= notesMaxLines) {
                                            Tuils.sendOutput(Color.RED, context, R.string.note_max_reached)
                                        }
                                        linesBefore = tv.getLineCount()
                                    }
                                })
                        }
                    }

                }
                Label.weather -> {
                    weatherRunnable = WeatherRunnable()
                    weatherColor = XMLPrefsManager.getColor(Theme.weather_color)
                    val where = XMLPrefsManager.get(Behavior.weather_location)
                    if (where.contains(",") || Tuils.isNumber(where)) handler.post(weatherRunnable)
                    showWeatherUpdate = XMLPrefsManager.getBoolean(Behavior.show_weather_updates)
                }
                Label.unlock -> {
                    unlockTimes = preferences.getInt(UNLOCK_KEY, 0)
                    unlockColor = XMLPrefsManager.getColor(Theme.unlock_counter_color)
                    unlockFormat = XMLPrefsManager.get(Behavior.unlock_counter_format) as String?
                    notAvailableText = XMLPrefsManager.get(Behavior.not_available_text) as String?
                    unlockTimeDivider = XMLPrefsManager.get(Behavior.unlock_time_divider) as String?
                    unlockTimeDivider =
                        Tuils.patternNewline.matcher(unlockTimeDivider).replaceAll(Tuils.NEWLINE) as String?
                    val start = XMLPrefsManager.get(Behavior.unlock_counter_cycle_start)
                    val p = Pattern.compile("(\\d{1,2}).(\\d{1,2})")
                    var m = p.matcher(start)
                    if (!m.find()) {
                        m = p.matcher(Behavior.unlock_counter_cycle_start.defaultValue())
                        m.find()
                    }
                    unlockHour = m.group(1).toInt()
                    unlockMinute = m.group(2).toInt()
                    unlockTimeOrder = XMLPrefsManager.getInt(Behavior.unlock_time_order)
                    nextUnlockCycleRestart = preferences.getLong(NEXT_UNLOCK_CYCLE_RESTART, 0)
                    m = timePattern.matcher(unlockFormat)
                    if (m.find()) {
                        var s = m.group(3)
                        if (s == null || s.length == 0) s = "1"
                        lastUnlocks = LongArray(s.toInt())
                        lastUnlocks?.let {
                            for (c in it.indices) {
                                lastUnlocks!![c] = -1
                            }
                        }
                        registerLockReceiver()
                        handler.post(unlockTimeRunnable)
                    } else {
                        lastUnlocks = null
                    }

                }
            }
        }

        var effectiveCount = 0

        val inputBottom = XMLPrefsManager.getBoolean(Ui.input_bottom)
        val layoutId = if (inputBottom) R.layout.input_down_layout else R.layout.input_up_layout

        val inflater = mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inputOutputView = inflater.inflate(layoutId, null)
        rootView.addView(inputOutputView)

        terminalView = inputOutputView.findViewById<View?>(R.id.terminal_view) as TextView
        terminalView.setOnTouchListener(this)
        (terminalView.getParent().getParent() as View).setOnTouchListener(this)

        Companion.applyBgRect(
            terminalView,
            bgRectColors[OUTPUT_BGCOLOR_INDEX]!!.toString(),
            bgColors[OUTPUT_BGCOLOR_INDEX].toString(),
            margins[OUTPUT_MARGINS_INDEX]!!,
            strokeWidth,
            cornerRadius
        )
        Companion.applyShadow(
            terminalView,
            outlineColors[OUTPUT_BGCOLOR_INDEX]!!.toString(),
            shadowXOffset,
            shadowYOffset,
            shadowRadius
        )

        val inputView = inputOutputView.findViewById<View?>(R.id.input_view) as EditText
        val prefixView = inputOutputView.findViewById<View?>(R.id.prefix_view) as TextView

        Companion.applyBgRect(
            inputOutputView.findViewById<View?>(R.id.input_group),
            bgRectColors[INPUT_BGCOLOR_INDEX]!!.toString(),
            bgColors[INPUT_BGCOLOR_INDEX].toString(),
            margins[INPUTAREA_MARGINS_INDEX]!!,
            strokeWidth,
            cornerRadius
        )
        Companion.applyShadow(
            inputView,
            outlineColors[INPUT_BGCOLOR_INDEX]!!.toString(),
            shadowXOffset,
            shadowYOffset,
            shadowRadius
        )
        Companion.applyShadow(
            prefixView,
            outlineColors[INPUT_BGCOLOR_INDEX]!!.toString(),
            shadowXOffset,
            shadowYOffset,
            shadowRadius
        )

        Companion.applyMargins(inputView, margins[INPUTFIELD_MARGINS_INDEX]!!)
        Companion.applyMargins(prefixView, margins[INPUTFIELD_MARGINS_INDEX]!!)

        var submitView = inputOutputView.findViewById<View?>(R.id.submit_tv) as ImageView?
        val showSubmit = XMLPrefsManager.getBoolean(Ui.show_enter_button)
        if (!showSubmit) {
            submitView!!.setVisibility(View.GONE)
            submitView = null
        }

        val showToolbar = XMLPrefsManager.getBoolean(Toolbar.show_toolbar)
        var backView: ImageButton? = null
        var nextView: ImageButton? = null
        var deleteView: ImageButton? = null
        var pasteView: ImageButton? = null

        if (!showToolbar) {
            inputOutputView.findViewById<View?>(R.id.tools_view).setVisibility(View.GONE)
            toolbarView = null
        } else {
            backView = inputOutputView.findViewById<View?>(R.id.back_view) as ImageButton?
            nextView = inputOutputView.findViewById<View?>(R.id.next_view) as ImageButton?
            deleteView = inputOutputView.findViewById<View?>(R.id.delete_view) as ImageButton?
            pasteView = inputOutputView.findViewById<View?>(R.id.paste_view) as ImageButton?

            toolbarView = inputOutputView.findViewById<View?>(R.id.tools_view)
            hideToolbarNoInput = XMLPrefsManager.getBoolean(Toolbar.hide_toolbar_no_input)

            Companion.applyBgRect(
                toolbarView!!,
                bgRectColors[TOOLBAR_BGCOLOR_INDEX]!!.toString(),
                bgColors[TOOLBAR_BGCOLOR_INDEX].toString(),
                margins[TOOLBAR_MARGINS_INDEX]!!,
                strokeWidth,
                cornerRadius
            )
        }

        mTerminalAdapter = TerminalManager(
            terminalView,
            inputView,
            prefixView,
            submitView,
            backView,
            nextView,
            deleteView,
            pasteView,
            context,
            mainPack,
            executer
        )

        if (XMLPrefsManager.getBoolean(Suggestions.show_suggestions)) {
            val sv =
                rootView.findViewById<View?>(R.id.suggestions_container) as HorizontalScrollView
            sv.setFocusable(false)
            sv.setOnFocusChangeListener(OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                if (hasFocus) {
                    v!!.clearFocus()
                }
            })
            Companion.applyBgRect(
                sv,
                bgRectColors[SUGGESTIONS_BGCOLOR_INDEX]!!.toString(),
                bgColors[SUGGESTIONS_BGCOLOR_INDEX].toString(),
                margins[SUGGESTIONS_MARGINS_INDEX]!!,
                strokeWidth,
                cornerRadius
            )

            val suggestionsView =
                rootView.findViewById<View?>(R.id.suggestions_group) as LinearLayout?

            suggestionsManager = SuggestionsManager(suggestionsView, mainPack, mTerminalAdapter)

            inputView.addTextChangedListener(
                SuggestionTextWatcher(
                    suggestionsManager,
                    OnTextChanged { currentText: kotlin.String?, before: Int ->
                        if (!hideToolbarNoInput) return@OnTextChanged
                        if (currentText!!.length == 0) toolbarView!!.setVisibility(View.GONE)
                        else if (before == 0) toolbarView!!.setVisibility(View.VISIBLE)
                    })
            )
        } else {
            rootView.findViewById<View?>(R.id.suggestions_group).setVisibility(View.GONE)
        }

        var drawTimes = XMLPrefsManager.getInt(Ui.text_redraw_times)
        if (drawTimes <= 0) drawTimes = 1
        OutlineTextView.redrawTimes = drawTimes
    }

    private fun invalidateUnlockText() {
        var cp = unlockFormat as kotlin.String

        cp = unlockCount.matcher(cp).replaceAll(unlockTimes.toString())
        cp = Tuils.patternNewline.matcher(cp).replaceAll(Tuils.NEWLINE)

        val m = advancement.matcher(cp)
        if (m.find()) {
            val denominator: Int = m.group(1).toInt()
            val divider = m.group(2)

            val lastCycleStart = nextUnlockCycleRestart - cycleDuration

            val elapsed = (System.currentTimeMillis() - lastCycleStart).toInt()
            val numerator = denominator * elapsed / cycleDuration

            cp = m.replaceAll(numerator.toString() + divider + denominator)
        }

        var s: CharSequence? =
            Tuils.span(cp, unlockColor)

        val timeMatcher = timePattern.matcher(cp)
        if (timeMatcher.find()) {
            val timeGroup = timeMatcher.group(1)
            var text = timeMatcher.group(2)
            if (text == null) text = whenPattern as kotlin.String?

            var cs: CharSequence? = Tuils.EMPTYSTRING

            var c: Int
            val change: Int
            if (unlockTimeOrder == UP_DOWN) {
                c = 0
                change = +1
            } else {
                c = lastUnlocks!!.size - 1
                change = -1
            }

            var counter = 0
            while (counter < lastUnlocks!!.size) {
                var t: kotlin.String? = text
                t = indexPattern.matcher(t).replaceAll((c + 1).toString())

                cs = TextUtils.concat(cs, t)

                val time: CharSequence?
                if (lastUnlocks!![c] > 0) time =
                    TimeManager.instance.getCharSequence(timeGroup, lastUnlocks!![c])
                else time = notAvailableText

                if (time == null) {
                    counter++
                    c += change
                    continue
                }

                cs = TextUtils.replace(
                    cs,
                    arrayOf<kotlin.String?>(whenPattern.toString()),
                    arrayOf<CharSequence>(time)
                )

                if (counter != lastUnlocks!!.size - 1) cs = TextUtils.concat(cs, unlockTimeDivider)
                counter++
                c += change
            }

            s = TextUtils.replace(
                s,
                arrayOf<kotlin.String?>(timeMatcher.group(0)),
                arrayOf<CharSequence?>(cs)
            )
        }
        if (s != null) {
            updateText(Label.unlock, s)
        }

    }

    companion object {

        private const val TAG = "UIManager"
        @kotlin.jvm.JvmField
        var ACTION_UPDATE_SUGGESTIONS: kotlin.String =
            BuildConfig.APPLICATION_ID + ".ui_update_suggestions"
        @kotlin.jvm.JvmField
        var ACTION_UPDATE_HINT: kotlin.String = BuildConfig.APPLICATION_ID + ".ui_update_hint"
        @kotlin.jvm.JvmField
        var ACTION_ROOT: kotlin.String = BuildConfig.APPLICATION_ID + ".ui_root"
        @kotlin.jvm.JvmField
        var ACTION_NOROOT: kotlin.String = BuildConfig.APPLICATION_ID + ".ui_noroot"
        @kotlin.jvm.JvmField
        var ACTION_LOGTOFILE: kotlin.String = BuildConfig.APPLICATION_ID + ".ui_log"
        @kotlin.jvm.JvmField
        var ACTION_CLEAR: kotlin.String = BuildConfig.APPLICATION_ID + "ui_clear"
        @kotlin.jvm.JvmField
        var ACTION_WEATHER: kotlin.String = BuildConfig.APPLICATION_ID + "ui_weather"
        var ACTION_WEATHER_GOT_LOCATION: kotlin.String =
            BuildConfig.APPLICATION_ID + "ui_weather_location"
        @kotlin.jvm.JvmField
        var ACTION_WEATHER_DELAY: kotlin.String = BuildConfig.APPLICATION_ID + "ui_weather_delay"
        @kotlin.jvm.JvmField
        var ACTION_WEATHER_MANUAL_UPDATE: kotlin.String =
            BuildConfig.APPLICATION_ID + "ui_weather_update"

        @kotlin.jvm.JvmField
        var FILE_NAME: kotlin.String = "fileName"
        @kotlin.jvm.JvmField
        var PREFS_NAME: kotlin.String = "ui"

        @kotlin.jvm.JvmStatic
        fun getListOfIntValues(values: kotlin.String, length: Int, defaultValue: Int): IntArray {
            var values = values
            val `is` = IntArray(length)
            values = removeSquareBrackets(values)
            val split: Array<kotlin.String?> =
                values.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var c = 0
            while (c < split.size) {
                try {
                    `is`[c] = split[c]?.toInt()!!
                } catch (e: Exception) {
                    `is`[c] = defaultValue
                }
                c++
            }
            while (c < split.size) `is`[c] = defaultValue

            return `is`
        }

        fun getListOfStringValues(
            values: kotlin.String,
            length: Int,
            defaultValue: kotlin.String?
        ): Array<kotlin.String?> {
            val `is` = arrayOfNulls<kotlin.String>(length)
            val split: Array<kotlin.String?> =
                values.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            var len: Int = min(split.size, `is`.size)
            System.arraycopy(split, 0, `is`, 0, len)

            while (len < `is`.size) `is`[len++] = defaultValue

            return `is`
        }

        private val sbPattern: Pattern = Pattern.compile("[\\[\\]\\s]")
        private fun removeSquareBrackets(s: kotlin.String): kotlin.String {
            return sbPattern.matcher(s).replaceAll(Tuils.EMPTYSTRING)
        }

        //    0 = ext hor
        //    1 = ext ver
        //    2 = int hor
        //    3 = int ver
        private fun applyBgRect(
            v: View,
            strokeColor: kotlin.String,
            bgColor: kotlin.String?,
            spaces: IntArray,
            strokeWidth: Int,
            cornerRadius: Int
        ) {
            try {
                val d = GradientDrawable()
                d.setShape(GradientDrawable.RECTANGLE)
                d.setCornerRadius(cornerRadius.toFloat())

                if (!(strokeColor.startsWith("#00") && strokeColor.length == 9)) {
                    d.setStroke(strokeWidth, Color.parseColor(strokeColor))
                }

                applyMargins(v, spaces)

                d.setColor(Color.parseColor(bgColor))
                v.setBackgroundDrawable(d)
            } catch (e: Exception) {
                Tuils.toFile(v.getContext(), e)
                Tuils.log(e)
            }
        }

        private fun applyMargins(v: View, margins: IntArray) {
            v.setPadding(margins[2], margins[3], margins[2], margins[3])

            val params = v.getLayoutParams()
            if (params is RelativeLayout.LayoutParams) {
                params.setMargins(margins[0], margins[1], margins[0], margins[1])
            } else if (params is LinearLayout.LayoutParams) {
                params.setMargins(margins[0], margins[1], margins[0], margins[1])
            }
        }

        private fun applyShadow(v: TextView, color: kotlin.String, x: Int, y: Int, radius: Float) {
            if (!(color.startsWith("#00") && color.length == 9)) {
                v.setShadowLayer(radius, x.toFloat(), y.toFloat(), Color.parseColor(color))
                v.setTag(OutlineTextView.SHADOW_TAG)

                //            if(radius > v.getPaddingTop()) v.setPadding(v.getPaddingLeft(), (int) Math.floor(radius), v.getPaddingRight(), (int) Math.floor(radius));
//            if(radius > v.getPaddingLeft()) v.setPadding((int) Math.floor(radius), v.getPaddingTop(), (int) Math.floor(radius), v.getPaddingBottom());
            }
        }

        @kotlin.jvm.JvmField
        var UNLOCK_KEY: kotlin.String = "unlockTimes"
        @kotlin.jvm.JvmField
        var NEXT_UNLOCK_CYCLE_RESTART: kotlin.String = "nextUnlockRestart"
    }
}

