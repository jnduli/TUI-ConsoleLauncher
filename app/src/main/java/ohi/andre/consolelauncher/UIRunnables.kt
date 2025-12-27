package ohi.andre.consolelauncher

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.StatFs
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ohi.andre.consolelauncher.UIManager.Label
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.TuiLocationManager
import ohi.andre.consolelauncher.managers.WeatherRepository
import ohi.andre.consolelauncher.managers.WeatherResponse
import ohi.andre.consolelauncher.managers.weatherURL
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.NetworkUtils
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.lang.reflect.Method
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

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

class TimeViewModel: ViewModel() {
    private val _currentTime = MutableStateFlow(getCurrentTime())
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    init {
        poll()
    }

    private fun poll() {
        viewModelScope.launch {
            while (true) {
                _currentTime.value = getCurrentTime()
                delay(1000)
            }
        }

    }
    private fun getCurrentTime(): String {
        val current = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())
        return current.format(Date())
    }

}

class TimeRunnable(uiManager: UIManager, handler: Handler) : UIRunnable(uiManager, handler, label=Label.time, rerunDelayMillis = TIME_RUNNABLE_DELAY_MS) {

    override fun text(): CharSequence {
        return TimeManager.instance.getCharSequence(
            uiManager.mContext,
            uiManager.getLabelSize(label),
            "%t0"
        )
    }
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


class RamRunnable(uiManager: UIManager, handler: Handler): UIRunnable(uiManager, handler, label=Label.ram, rerunDelayMillis = RAM_RUNNABLE_DELAY_MS) {

    override fun text(): CharSequence {
        val activityManager = uiManager.mContext.getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memory)
        val available = memory.availMem
        val totalMem = memory.totalMem
        return ByteFormatter.toHumanReadableSize(available) + " / " + ByteFormatter.toHumanReadableSize(totalMem)
    }
}

class StorageRunnable(uiManager: UIManager, handler: Handler): UIRunnable(uiManager, handler, label=Label.storage, rerunDelayMillis = STORAGE_RUNNABLE_DELAY_MS) {
    override fun text(): CharSequence {
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

class NetworkRunnable(uiManager: UIManager, handler: Handler): UIRunnable(uiManager, handler, label=Label.network, rerunDelayMillis = NETWORK_RUNNABLE_DELAY_MS) {
    val connectivityManager: ConnectivityManager = uiManager.mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager: WifiManager = uiManager.mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
    val mBluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    val telephonyManager: TelephonyManager = uiManager.mContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    init {
        ActivityCompat.requestPermissions(
            uiManager.mContext as Activity, LauncherActivity.REQUIRED_CONNECTIVITY_PERMISSIONS,
            LauncherActivity.CONNECTIVITY_PERMISSION)
    }

    override fun text(): CharSequence {
        // return "%(WiFi - %wn/%[Mobile Data: %d3/No Internet access])";
        return "Wifi: " + wifi() + " Mobile: " + mobile() + " Blueth: " + bluetooth()
    }

    fun wifi(): CharSequence {
        val wifiConnectionInfo = wifiManager.connectionInfo
        val wifiName = wifiConnectionInfo.ssid
        return wifiName
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

        // I want: true/No Internet Access
        /**
        val mTelephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return "unknown"
        }
        */

    }

    fun bluetooth(): CharSequence {
        if (mBluetoothAdapter.isEnabled == true) {
            return "on"
        }
        return "off"
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