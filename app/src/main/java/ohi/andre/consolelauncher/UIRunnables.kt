package ohi.andre.consolelauncher

import android.app.ActivityManager
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.os.Handler
import android.os.StatFs
import android.telephony.TelephonyManager
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ohi.andre.consolelauncher.UIManager.Label
import ohi.andre.consolelauncher.managers.TuiLocationManager
import ohi.andre.consolelauncher.managers.WeatherRepository
import ohi.andre.consolelauncher.managers.weatherURL
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TIME_RUNNABLE_DELAY_MS: Long = 1 * 1000 // 1 second
const val RAM_RUNNABLE_DELAY_MS: Long = 5 * 1000 // 5 seconds
const val STORAGE_RUNNABLE_DELAY_MS: Long =  60 * 1000 // 1 minute
const val NETWORK_RUNNABLE_DELAY_MS: Long = 10 * 1000 // 10 seconds
const val WEATHER_RUNNABLE_DELAY_MS: Long =  120 * 1000 // 1 minute
const val TAG = "UIRunnables"

abstract class UIRunnable(val uiManager: UIManager, val handler: Handler, val label: UIManager.Label, val rerunDelayMillis: Long) : Runnable{

    abstract fun text(): CharSequence

    override fun run() {
        uiManager.updateText(label, text())
        handler.postDelayed(this, rerunDelayMillis)
    }
}


fun colorString(content: CharSequence, fg: Int): SpannableString {
    val spannableString = SpannableString(content)
    spannableString.setSpan(ForegroundColorSpan(fg), 0, content.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return spannableString
}

abstract class PollViewModel(application: Application, val delayMillis: Long, val fg_color: Int): AndroidViewModel(application) {

    private val _currentText = MutableStateFlow(colorString(getText(), fg_color))

    val currentText: StateFlow<CharSequence> = _currentText.asStateFlow()

    init {
        poll()
    }

    private fun poll() {
        viewModelScope.launch {
            while (true) {
                _currentText.value = colorString(getText(), fg_color)
                delay(delayMillis)
            }
        }
    }

    abstract fun getText(): CharSequence

}

class TimeViewModel(application: Application) : PollViewModel(application, TIME_RUNNABLE_DELAY_MS, XMLPrefsManager.getColor(Theme.time_color)) {
    override fun getText(): CharSequence {
        val current = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
        return current.format(Date())
    }
}

class MemoryViewModel(application: Application) : PollViewModel(application, RAM_RUNNABLE_DELAY_MS, XMLPrefsManager.getColor(Theme.ram_color) ) {
    private val am = application.getSystemService((Context.ACTIVITY_SERVICE)) as ActivityManager
    override fun getText(): CharSequence {
        val memoryInfo = ActivityManager.MemoryInfo()
        if (am == null) {
            return "n/a"
        }
        am.getMemoryInfo(memoryInfo)
        return ByteFormatter.toHumanReadableSize(memoryInfo.availMem) + " / " + ByteFormatter.toHumanReadableSize(memoryInfo.totalMem)
    }
}

class StorageViewModel(application: Application): PollViewModel(application,STORAGE_RUNNABLE_DELAY_MS, XMLPrefsManager.getColor(Theme.storage_color)) {

    override fun getText(): CharSequence {
        val internalStorageSize = getSpaceInBytes(Environment.getDataDirectory())
        // TODO(jnduli): handle externalStorageSize
        val externalStorageSize = getSpaceInBytes(Environment.getExternalStorageDirectory())
        return "Internal Strg: " + ByteFormatter.toHumanReadableSize(internalStorageSize.getOrElse(StorageType.Available, { 0 })) + " / " + ByteFormatter.toHumanReadableSize(internalStorageSize.getOrElse(StorageType.Total,
            { 0 }))
    }

    private enum class StorageType {
        Available,
        Total,
    }

    private fun getSpaceInBytes(dir: File): Map<StorageType, Long> {
        val statFs = StatFs(dir.getAbsolutePath())
        val blockSize = statFs.getBlockSize().toLong()
        return mapOf(StorageType.Available to statFs.getAvailableBlocks().toLong() * blockSize, StorageType.Total to statFs.getBlockCount().toLong() * blockSize)
    }
}

class NetworkViewModel(application: Application) : AndroidViewModel(application) {
    private val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val connectionStatus: StateFlow<String> = callbackFlow {
        val callback = object: NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend("Online")
            }

            override fun onLost(network: Network) {
                trySend("Offline")
            }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addTransportType(
            NetworkCapabilities.TRANSPORT_CELLULAR).addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()

        cm.registerNetworkCallback(request, callback)
        awaitClose { cm.unregisterNetworkCallback(callback)}
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(NETWORK_RUNNABLE_DELAY_MS), "Checking...")
}

class WifiViewModel(application: Application) : AndroidViewModel(application) {
    private val wifi_manager = application.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _ssid = MutableStateFlow("n/a")
    val ssid = _ssid.asStateFlow()

    fun updateSsid() {
        val info = wifi_manager.connectionInfo.ssid
        val ssid = info.removeSurrounding("\"")
        _ssid.value = ssid
    }
}

class BlueToothViewModel(application: Application) : AndroidViewModel(application) {
    private val mBluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val _status = MutableStateFlow("n/a")
    val status = _status.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _status.value = colorString(bluetooth(), Color.RED).toString()
                delay(NETWORK_RUNNABLE_DELAY_MS)
            }
        }
    }

    fun bluetooth(): CharSequence {
        if (mBluetoothAdapter.isEnabled == true) {
            return "on"
        }
        return "off"
    }
}

class MobileViewModel(application: Application) : AndroidViewModel(application) {
    private val telephonyManager = application.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val _status = MutableStateFlow("n/a")
    val status = _status.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _status.value = colorString(mobile(), Color.RED).toString()
                delay(NETWORK_RUNNABLE_DELAY_MS)
            }
        }
    }

    fun mobile(): CharSequence {
        try {
            val networkType = telephonyManager.networkType
            when (networkType) {
                TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> return "2g"
                TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP -> return "3g"
                TelephonyManager.NETWORK_TYPE_LTE -> return "4g"
                else -> return "n/a"
            }
        } catch(e: SecurityException) {
            Log.e(TAG, e.toString())
        }
        return "n/a"
    }

}


class BatteryViewModel(application: Application) : AndroidViewModel(application) {

    private fun getColor(level: Int): Int {
        if (level > XMLPrefsManager.getInt(Behavior.battery_medium)) {
            return XMLPrefsManager.getColor(Theme.battery_color_high)
        }
        if (level > XMLPrefsManager.getInt(Behavior.battery_low)) {
            return XMLPrefsManager.getColor(Theme.battery_color_medium)
        }
        return XMLPrefsManager.getColor(Theme.battery_color_low)
    }

    val batteryStatus: StateFlow<CharSequence> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let {
                    val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = (level * 100 / scale.toFloat()).toInt()

                    val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL

                    val chargingLabel = if (isCharging) " (Charging)" else ""
                    trySend(colorString("$batteryPct%$chargingLabel", getColor(level)))
                }
            }
        }

        // Register the receiver
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        application.registerReceiver(receiver, filter)

        // Cleanup when the flow is cancelled
        awaitClose {
            application.unregisterReceiver(receiver)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Reading..."
    )
}

/**
 * An object containing the logic and constants for converting raw byte counts
 * into human-readable strings (KB, MB, GB, etc.).
 */
object ByteFormatter {
    
    // Using binary multipliers (powers of 1024) - Standard for OS/memory
    private const val KILOBYTE: Long = 1024
    private const val MEGABYTE: Long = KILOBYTE * 1024
    private const val GIGABYTE: Long = MEGABYTE * 1024
    private const val TERABYTE: Long = GIGABYTE * 1024
    private const val PETABYTE: Long = TERABYTE * 1024
    
    // Used for formatting the output string to two decimal places
    private val DECIMAL_FORMAT = DecimalFormat("#.##")
    
    fun toHumanReadableSize(bytes: Long): String {
        // Handle negative or zero bytes
        if (bytes <= 0) return "0 Bytes"

        // Determine the correct unit and calculate the result
        return when {
            bytes >= PETABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / PETABYTE)} PB"
            bytes >= TERABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / TERABYTE)} TB"
            bytes >= GIGABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / GIGABYTE)} GB"
            bytes >= MEGABYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / MEGABYTE)} MB"
            bytes >= KILOBYTE ->
                "${DECIMAL_FORMAT.format(bytes.toDouble() / KILOBYTE)} KB"

            else ->
                "$bytes Bytes" // Less than 1 KB
        }.toString()
    }
}




class WeatherRunnables(uiManager: UIManager, handler: Handler): UIRunnable(uiManager, handler, label=Label.weather, rerunDelayMillis = WEATHER_RUNNABLE_DELAY_MS) {
    val weatherRepository = WeatherRepository()
    val key = XMLPrefsManager.get(Behavior.weather_key)
    var isActive = true
    var weather_details: CharSequence = ""

    init {
        val location = TuiLocationManager.instance(uiManager.mContext)
        location.add(UIManager.ACTION_WEATHER_GOT_LOCATION)
    }

    public fun disable() {
        isActive = false
    }

    public fun set_weather(weather: CharSequence) {
        weather_details = weather
    }

    fun updateWeather() {
        if (isActive == false) {
            return
        }
        val url = weatherURL(key, uiManager.lastLatitude, uiManager.lastLongitude, XMLPrefsManager.get(Behavior.weather_temperature_measure))
        // TODO(jnduli): not a great solution because updates for weather aren't done immediately
        // won't work well if/when I increase the weather time out
        weatherRepository.fetchWeather(url) {weatherData ->
            run {
                if (weatherData != null) {
                    val weather_string: StringBuilder = StringBuilder().also {
                        it.append("Weather: ")
                    }
                    weatherData.weather.forEach { weather_string.append(":$it.main") }
                    val temp = weatherData.main.temp
                    weather_string.append(" Temp: $temp")
                    weather_details = weather_string
                }
            }
        }
    }

    override fun text(): CharSequence {
        updateWeather()
        return weather_details
    }
}