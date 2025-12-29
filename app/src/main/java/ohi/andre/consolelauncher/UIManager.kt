package ohi.andre.consolelauncher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.OnDoubleTapListener
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
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.commands.main.specific.RedirectCommand
import ohi.andre.consolelauncher.managers.TerminalManager
import ohi.andre.consolelauncher.managers.suggestions.SuggestionTextWatcher
import ohi.andre.consolelauncher.managers.suggestions.SuggestionsManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Suggestions
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.managers.xml.options.Toolbar
import ohi.andre.consolelauncher.managers.xml.options.Ui
import ohi.andre.consolelauncher.tuils.OutlineTextView
import ohi.andre.consolelauncher.tuils.Tuils
import ohi.andre.consolelauncher.tuils.interfaces.CommandExecuter
import ohi.andre.consolelauncher.tuils.interfaces.OnRedirectionListener
import ohi.andre.consolelauncher.tuils.interfaces.OnTextChanged
import ohi.andre.consolelauncher.tuils.stuff.PolicyReceiver
import java.io.File
import java.io.FileOutputStream
import java.lang.String
import java.util.Calendar
import java.util.regex.Pattern
import kotlin.Array
import kotlin.Boolean
import kotlin.CharSequence
import kotlin.Exception
import kotlin.Float
import kotlin.Int
import kotlin.IntArray
import kotlin.arrayOf
import kotlin.arrayOfNulls
import kotlin.math.min
import kotlin.toString


class TextUpdateManager(private val textView: TextView, private val dataFlow: StateFlow<CharSequence>) : DefaultLifecycleObserver {
    private var job: Job? = null
    override fun onStart(owner: LifecycleOwner) {
        job = owner.lifecycleScope.launch {
            dataFlow.collect {value ->
                textView.text = value
            }
        }
        super.onStart(owner)
    }
    override fun onStop(owner: LifecycleOwner) {
        job?.cancel()
    }
}

class UIManager(
    context: Context,
    rootView: ViewGroup,
    mainPack: MainPack?,
    canApplyTheme: Boolean,
    executer: CommandExecuter?,
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

    public lateinit var mContext: Context

    private var handler = Handler()

    private var policy: DevicePolicyManager?
    private var component: ComponentName?
    private var gestureDetector: GestureDetectorCompat? = null

    var preferences: SharedPreferences

    private val imm: InputMethodManager
    private var mTerminalAdapter: TerminalManager?

    var hideToolbarNoInput: Boolean = false
    var toolbarView: View? = null

    //    never access this directly, use getLabelView
    // TODO: change this to a map to enable easier modification and access
    val mapLabelViews = loadTextViews(rootView)


    data class LabelView(val textView: TextView, val size: Int, val color: Int, val show: Boolean)

    private fun loadTextViews(rootView: ViewGroup): Map<Label, LabelView?> {
       return  mapOf(
           Label.device to LabelView(
               rootView.findViewById<View?>(R.id.tv6) as TextView,
               XMLPrefsManager.getInt(Ui.device_size),
               XMLPrefsManager.getColor(Theme.device_color),
               XMLPrefsManager.getBoolean(Ui.show_device_name),
           ),
        )
    }

    public fun updateText(l: Label, s: CharSequence) {
        val dataLabel = mapLabelViews[l]
        if (dataLabel?.show == false || s.isEmpty()) {
            dataLabel?.textView?.visibility = View.GONE
            return
        }
        val color = dataLabel?.color?: Color.RED
        val coloredSpan = Tuils.span(s, color)
        dataLabel?.textView?.visibility = View.VISIBLE
        dataLabel?.textView?.text = coloredSpan
    }

    private var suggestionsManager: SuggestionsManager? = null

    private val terminalView: TextView

    private var doubleTapCmd: String?
    private var lockOnDbTap: Boolean

    private val receiver: BroadcastReceiver

    @kotlin.jvm.JvmField
    var pack: MainPack? = null

    fun dispose() {
        handler.removeCallbacksAndMessages(null)
        suggestionsManager?.dispose()

        LocalBroadcastManager.getInstance(mContext.applicationContext)
            .unregisterReceiver(receiver)
        Tuils.unregisterBatteryReceiver(mContext)

        Tuils.cancelFont()
    }

    fun openKeyboard() {
        mTerminalAdapter?.requestInputFocus()
        imm.showSoftInput(mTerminalAdapter?.inputView, InputMethodManager.SHOW_IMPLICIT)
        //        mTerminalAdapter.scrollToEnd();
    }

    fun closeKeyboard() {
        imm.hideSoftInputFromWindow(mTerminalAdapter?.inputWindowToken, 0)
    }

    fun onStart(openKeyboardOnStart: Boolean) {
        if (openKeyboardOnStart) openKeyboard()
    }

    fun setInput(s: String?) {
        if (s == null) return

        mTerminalAdapter?.input = s as kotlin.String?
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
        if (event != null) {
            gestureDetector?.onTouchEvent(event)
        }
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
                val action = intent.action

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
                        fos.write(mTerminalAdapter?.terminalText?.toByteArray())
                        Tuils.sendOutput(context, "Logged to " + file.absolutePath)
                    } catch (e: Exception) {
                        Log.e(TAG, e.toString())
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
                    // weatherRunnable?.set_weather(s)

                    /**
                    if (showWeatherUpdate) {
                        val message =
                            context.getString(R.string.weather_updated) + Tuils.SPACE + c.get(
                                Calendar.HOUR_OF_DAY
                            ) + "." + c.get(Calendar.MINUTE) + Tuils.SPACE + "(" + lastLatitude + ", " + lastLongitude + ")" + " to: " + s
                        Tuils.sendOutput(context, message, TerminalManager.CATEGORY_OUTPUT)
                    }
                    */
                } else if (action == ACTION_WEATHER_GOT_LOCATION) {
                    /**
                    if (intent.getBooleanExtra(TuiLocationManager.FAIL, false)) {
                        weatherRunnable?.disable()
                        weatherRunnable?.set_weather(context.getString(R.string.location_error))
                    } else {
                        lastLatitude = intent.getDoubleExtra(TuiLocationManager.LATITUDE, 0.0)
                        lastLongitude = intent.getDoubleExtra(TuiLocationManager.LONGITUDE, 0.0)
                        location = Tuils.locationName(context, lastLatitude, lastLongitude) as String?

                        if (!weatherPerformedStartupRun || XMLPrefsManager.wasChanged(
                                Behavior.weather_key,
                                false
                            )
                        ) {
                            if (weatherRunnable != null) {
                                handler.removeCallbacks(weatherRunnable!!)
                                handler.post(weatherRunnable!!)
                            }
                        }
                    }
                    */
                } else if (action == ACTION_WEATHER_DELAY) {
                    val c = Calendar.getInstance()
                    c.timeInMillis = System.currentTimeMillis() + 1000 * 10
                    /**

                    if (showWeatherUpdate) {
                        val message =
                            context.getString(R.string.weather_error) + Tuils.SPACE + c.get(
                                Calendar.HOUR_OF_DAY
                            ) + "." + c.get(Calendar.MINUTE)
                        Tuils.sendOutput(context, message, TerminalManager.CATEGORY_OUTPUT)
                    }

                    if (weatherRunnable != null) {
                        handler.removeCallbacks(weatherRunnable!!)
                        handler.postDelayed(weatherRunnable!!, (1000 * 60).toLong())
                    }
                    */
                } else if (action == ACTION_WEATHER_MANUAL_UPDATE) {
                }
            }
        }

        LocalBroadcastManager.getInstance(context.applicationContext)
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
            rootView.viewTreeObserver.addOnGlobalLayoutListener(OnGlobalLayoutListener {
                val heightDiff = rootView.rootView.height - rootView.height
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
                    override fun onDown(p0: MotionEvent): Boolean {
                        return false
                    }

                    override fun onShowPress(p0: MotionEvent) {}

                    override fun onSingleTapUp(p0: MotionEvent): Boolean {
                        return false
                    }

                    override fun onScroll(
                        p0: MotionEvent,
                        p1: MotionEvent,
                        distanceY: Float,
                        p3: Float
                    ): Boolean {
                        return false
                    }

                    override fun onLongPress(p0: MotionEvent) {}

                    override fun onFling(
                        p0: MotionEvent,
                        p1: MotionEvent,
                        velocityY: Float,
                        p3: Float
                    ): Boolean {
                        return false
                    }
                })

            gestureDetector?.setOnDoubleTapListener(object : OnDoubleTapListener {
                override fun onSingleTapConfirmed(p0: MotionEvent): Boolean {
                    return false
                }

                override fun onDoubleTapEvent(p0: MotionEvent): Boolean {
                    return true
                }

                override fun onDoubleTap(p0: MotionEvent): Boolean {
                    if (doubleTapCmd != null && doubleTapCmd!!.isNotEmpty()) {
                        val input = mTerminalAdapter?.input
                        mTerminalAdapter?.input = doubleTapCmd as kotlin.String?
                        mTerminalAdapter?.simulateEnter()
                        mTerminalAdapter?.input = input
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
        val metrics = mContext.resources.displayMetrics
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
            val tv = view?.textView ?: continue
            tv.setOnTouchListener(this)
            tv.setTypeface(Tuils.getTypeface(context))
            if (view?.show == false) {
                val viewParent = tv.parent as LinearLayout
                viewParent.removeView(tv)
            }
            if (label != Label.notes) {
                tv.isVerticalScrollBarEnabled = false
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
                Label.device -> {
                    val username = XMLPrefsManager.get(Ui.username)
                    var deviceName = XMLPrefsManager.get(Ui.deviceName)?: Build.DEVICE
                    val content = "$username: $deviceName"
                    val span = Tuils.span(content, XMLPrefsManager.getColor(Theme.device_color))
                    updateText(label, span)
                }
                Label.battery -> TODO()
                Label.network -> TODO()
                Label.notes -> TODO()
                Label.weather -> TODO()
                Label.unlock -> TODO()
                Label.ram -> TODO()
                Label.time -> TODO()
                Label.storage -> TODO()
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
        (terminalView.parent.parent as View).setOnTouchListener(this)

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
            inputOutputView.findViewById<View?>(R.id.input_group)!!,
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
            submitView!!.visibility = View.GONE
            submitView = null
        }

        val showToolbar = XMLPrefsManager.getBoolean(Toolbar.show_toolbar)
        var backView: ImageButton? = null
        var nextView: ImageButton? = null
        var deleteView: ImageButton? = null
        var pasteView: ImageButton? = null

        if (!showToolbar) {
            inputOutputView.findViewById<View?>(R.id.tools_view)?.visibility = View.GONE
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
            sv.onFocusChangeListener = OnFocusChangeListener { v: View?, hasFocus: Boolean ->
                if (hasFocus) {
                    v!!.clearFocus()
                }
            }
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
                        if (currentText!!.isEmpty()) toolbarView!!.visibility = View.GONE
                        else if (before == 0) toolbarView!!.visibility = View.VISIBLE
                    })
            )
        } else {
            rootView.findViewById<View?>(R.id.suggestions_group)?.visibility = View.GONE
        }

        var drawTimes = XMLPrefsManager.getInt(Ui.text_redraw_times)
        if (drawTimes <= 0) drawTimes = 1
        OutlineTextView.redrawTimes = drawTimes
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
                    Log.e(TAG, e.toString())
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
                d.shape = GradientDrawable.RECTANGLE
                d.cornerRadius = cornerRadius.toFloat()

                if (!(strokeColor.startsWith("#00") && strokeColor.length == 9)) {
                    d.setStroke(strokeWidth, Color.parseColor(strokeColor))
                }

                applyMargins(v, spaces)

                d.setColor(Color.parseColor(bgColor))
                v.setBackgroundDrawable(d)
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
                Tuils.toFile(v.context, e)
            }
        }

        private fun applyMargins(v: View, margins: IntArray) {
            v.setPadding(margins[2], margins[3], margins[2], margins[3])

            val params = v.layoutParams
            if (params is RelativeLayout.LayoutParams) {
                params.setMargins(margins[0], margins[1], margins[0], margins[1])
            } else if (params is LinearLayout.LayoutParams) {
                params.setMargins(margins[0], margins[1], margins[0], margins[1])
            }
        }

        private fun applyShadow(v: TextView, color: kotlin.String, x: Int, y: Int, radius: Float) {
            if (!(color.startsWith("#00") && color.length == 9)) {
                v.setShadowLayer(radius, x.toFloat(), y.toFloat(), Color.parseColor(color))
                v.tag = OutlineTextView.SHADOW_TAG
            }
        }

        @kotlin.jvm.JvmField
        var UNLOCK_KEY: kotlin.String = "unlockTimes"
        @kotlin.jvm.JvmField
        var NEXT_UNLOCK_CYCLE_RESTART: kotlin.String = "nextUnlockRestart"
    }
}

