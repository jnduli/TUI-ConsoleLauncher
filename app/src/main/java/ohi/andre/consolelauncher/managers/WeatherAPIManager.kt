package ohi.andre.consolelauncher.managers

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import java.io.IOException


import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.Tuils
import okhttp3.CacheControl

@Serializable
data class WeatherResponse(
    val coord: Coordinates,
    val weather: List<WeatherDescription>,
    val main: MainStats,
    val wind: Wind,
    val name: String
)

@Serializable
data class Coordinates(val lon: Double, val lat: Double)

@Serializable
data class WeatherDescription(val main: String, val description: String)

@Serializable
data class MainStats(
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    val humidity: Int,
    val pressure: Int
)

@Serializable
data class Wind(val speed: Double, val deg: Int)

val TAG = "WeatherAPIManager"

fun weatherURL(appId: String, latitude: Double, longitude: Double, units: String) : String {
    return "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=$appId&units=$units"
}


class WeatherRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true } // Skips fields we didn't define

    fun fetchWeather(url: String, callback: (WeatherResponse?) -> Unit) {
        Log.i(TAG, "Fetching data for: $url")
        // if (!Tuils.hasInternetAccess()) {
        //    return
        // }
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .addHeader(
                "User-Agent",
                "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:15.0) Gecko/20100101 Firefox/15.0.1"
            )
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                Log.e(TAG, e.toString())
                callback(null)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    if (!it.isSuccessful) callback(null)
                    val bodyString = it.body?.string()
                    if (bodyString != null) {
                        val weatherData = json.decodeFromString<WeatherResponse>(bodyString)
                        callback(weatherData)
                    } else {
                        callback(null)
                    }
                }
            }
        })
    }
}