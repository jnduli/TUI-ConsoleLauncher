package ohi.andre.consolelauncher.managers

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import java.io.IOException


import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.tuils.SystemUtils

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
data class WeatherDescription(
    val id: Int = 0,
    val main: String,
    val description: String,
    /** e.g. "01d" / "01n"; the trailing letter tells day from night. */
    val icon: String = "",
)

@Serializable
data class MainStats(
    val temp: Double,
    @SerialName("feels_like") val feelsLike: Double,
    val humidity: Int,
    val pressure: Int
)

@Serializable
data class Wind(val speed: Double, val deg: Int)

val WEATHER_TAG = "WeatherAPIManager"

fun weatherURL(appId: String, latitude: Double, longitude: Double, units: String) : String {
    return "https://api.openweathermap.org/data/2.5/weather?lat=$latitude&lon=$longitude&appid=$appId&units=$units"
}


/** Maps an OpenWeatherMap condition code (https://openweathermap.org/weather-conditions) to an emoji. */
fun weatherEmoji(description: WeatherDescription): String {
    val night = description.icon.endsWith("n")
    return when (description.id) {
        in 200..299 -> "⛈️"
        in 300..399 -> "🌦️"
        511 -> "🌨️"
        in 500..599 -> "🌧️"
        in 611..616 -> "🌨️"
        in 600..699 -> "❄️"
        762 -> "🌋"
        781 -> "🌪️"
        711, 731, 751, 761, 771 -> "💨"
        in 700..799 -> "🌫️"
        800 -> if (night) "🌙" else "☀️"
        801 -> if (night) "🌙" else "🌤️"
        802 -> "⛅"
        803 -> "🌥️"
        804 -> "☁️"
        else -> "🌡️"
    }
}

private fun temperatureUnit(units: String): String = when (units) {
    "imperial" -> "°F"
    "standard" -> "K"
    else -> "°C"
}

/**
 * Fills in [format] (see Behavior.weather_format). Placeholders: %emoji, %main, %desc, %temp,
 * %feels, %unit, %humidity, %wind, %city. With several conditions, each is joined with ", ".
 */
fun formatWeather(format: String, data: WeatherResponse, units: String): String {
    fun Double.rounded() = Math.round(this).toString()
    val values = linkedMapOf(
        "%emoji" to data.weather.joinToString("") { weatherEmoji(it) },
        "%main" to data.weather.joinToString(", ") { it.main },
        "%desc" to data.weather.joinToString(", ") { it.description },
        "%temp" to data.main.temp.rounded(),
        "%feels" to data.main.feelsLike.rounded(),
        "%unit" to temperatureUnit(units),
        "%humidity" to data.main.humidity.toString(),
        "%wind" to data.wind.speed.toString(),
        "%city" to data.name,
    )
    return values.entries.fold(format) { acc, (key, value) -> acc.replace(key, value) }
}

class WeatherRepository {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true } // Skips fields we didn't define

    fun fetchWeather(url: String, callback: (WeatherResponse?) -> Unit) {
        Log.i(WEATHER_TAG, "Fetching data for: $url")
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
                Log.e(WEATHER_TAG, e.toString())
                callback(null)
            }

            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val bodyString = it.body?.string()
                    if (!it.isSuccessful || bodyString == null) {
                        Log.e(WEATHER_TAG, "Weather request failed: ${it.code} $bodyString")
                        callback(null)
                        return
                    }
                    val weatherData = try {
                        json.decodeFromString<WeatherResponse>(bodyString)
                    } catch (e: Exception) {
                        Log.e(WEATHER_TAG, "Couldn't parse weather response", e)
                        null
                    }
                    callback(weatherData)
                }
            }
        })
    }
}