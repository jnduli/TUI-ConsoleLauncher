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
import android.support.v4.app.ActivityCompat
import android.support.v4.content.LocalBroadcastManager
import android.telephony.TelephonyManager
import ohi.andre.consolelauncher.UIManager.Label
import ohi.andre.consolelauncher.managers.HTMLExtractManager
import ohi.andre.consolelauncher.managers.TimeManager
import ohi.andre.consolelauncher.managers.TuiLocationManager
import ohi.andre.consolelauncher.managers.xml.XMLPrefsManager
import ohi.andre.consolelauncher.managers.xml.options.Behavior
import ohi.andre.consolelauncher.managers.xml.options.Theme
import ohi.andre.consolelauncher.tuils.NetworkUtils
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.lang.reflect.Method
import java.text.DecimalFormat
import java.util.Locale
import java.util.regex.Pattern


abstract class UIRunnable(val uiManager: UIManager, val handler: Handler, val label: UIManager.Label, val rerunDelayMillis: Long) : Runnable{

    abstract fun text(): CharSequence

    override fun run() {
        uiManager.updateText(label, text())
        handler.postDelayed(this, rerunDelayMillis)
    }
}

class TimeRunnable(uiManager: UIManager, handler: Handler) : UIRunnable(uiManager, handler, label=Label.time, rerunDelayMillis = 1000) {

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


class RamRunnable(uiManager: UIManager, handler: Handler): UIRunnable(uiManager, handler, label=Label.ram, rerunDelayMillis = 3000) {

    override fun text(): CharSequence {
        val activityManager = uiManager.mContext.getSystemService(Activity.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memory)
        val available = memory.availMem
        val totalMem = memory.totalMem
        return ByteFormatter.toHumanReadableSize(available) + " / " + ByteFormatter.toHumanReadableSize(totalMem)
    }
}

class StorageRunnable(uiManager: UIManager, handler: Handler): UIRunnable(uiManager, handler, label=Label.storage, rerunDelayMillis = 60 * 1000) {
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

class NetworkRunnable(uiManager: UIManager, handler: Handler): UIRunnable(uiManager, handler, label=Label.network, rerunDelayMillis = 1000) {
    val connectivityManager: ConnectivityManager = uiManager.mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager: WifiManager = uiManager.mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE) as WifiManager
    val mBluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    val telephonyManager: TelephonyManager = uiManager.mContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

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
        return "N/A"
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
        val networkType = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            checkNotNull(mTelephonyManager)
            networkType = mTelephonyManager.getDataNetworkType()
        }
        when (networkType) {
            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT, TelephonyManager.NETWORK_TYPE_IDEN -> return "2g"
            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0, TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA, TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_HSPAP -> return "3g"
            TelephonyManager.NETWORK_TYPE_LTE -> return "4g"
            else -> return "unknown"
        }
        var mobileOn = false
        try {
            if (method != null && connectivityManager != null ) {
                mobileOn = method!!.invoke(connectivityManager) as Boolean
            }
        } catch (e: Exception) {
        }

        var mobileType: java.lang.String? = null
        if (mobileOn) {
            mobileType = Tuils.getNetworkType(mContext) as java.lang.String?
        } else {
            mobileType = "unknown" as java.lang.String?
        }
        */

    }

    fun bluetooth(): CharSequence {
        return "N/A"
        //            bluetooth
        // val bluetoothOn = mBluetoothAdapter != null && mBluetoothAdapter?.isEnabled() == true

    }
}

class WeatherRunnables(uiManager: UIManager, handler: Handler): UIRunnable(uiManager, handler, label=Label.weather, rerunDelayMillis = 60 * 1000) {
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

    fun send(url: CharSequence) {
        val intent = Intent(HTMLExtractManager.ACTION_WEATHER)
        intent.putExtra(XMLPrefsManager.VALUE_ATTRIBUTE, url )
        intent.putExtra(HTMLExtractManager.BROADCAST_COUNT, HTMLExtractManager.broadcastCount)
        LocalBroadcastManager.getInstance(uiManager.mContext.applicationContext).sendBroadcast(intent)
    }

    fun getWeatherUrl(latitude: Double, longitude: Double): String {
        return "https://api.openweathermap.org/data/2.5/weather?" + "lat=" + latitude + "&lon=" + longitude + "&appid=" + key + "&units=" + XMLPrefsManager.get( Behavior.weather_temperature_measure )
    }

    override fun text(): CharSequence {
        val url = getWeatherUrl(uiManager.lastLatitude, uiManager.lastLongitude)
        if (isActive == true) {
            send(url)
        }
        return weather_details
    }
}