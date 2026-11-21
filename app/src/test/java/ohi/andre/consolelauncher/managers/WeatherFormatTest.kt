package ohi.andre.consolelauncher.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherFormatTest {
    private fun response(vararg conditions: WeatherDescription) = WeatherResponse(
        coord = Coordinates(0.0, 0.0),
        weather = conditions.toList(),
        main = MainStats(temp = 21.6, feelsLike = 20.4, humidity = 60, pressure = 1012),
        wind = Wind(speed = 3.5, deg = 90),
        name = "Nairobi",
    )

    @Test
    fun fillsAllPlaceholders() {
        val data = response(WeatherDescription(800, "Clear", "clear sky", "01d"))
        assertEquals(
            "☀️ Clear (clear sky) 22°C feels 20, 60% 3.5 Nairobi",
            formatWeather("%emoji %main (%desc) %temp%unit feels %feels, %humidity% %wind %city", data, "metric"),
        )
    }

    @Test
    fun usesNightEmojiAndImperialUnit() {
        val data = response(WeatherDescription(800, "Clear", "clear sky", "01n"))
        assertEquals("🌙 22°F", formatWeather("%emoji %temp%unit", data, "imperial"))
    }

    @Test
    fun joinsMultipleConditions() {
        val data = response(
            WeatherDescription(501, "Rain", "moderate rain", "10d"),
            WeatherDescription(211, "Thunderstorm", "thunderstorm", "11d"),
        )
        assertEquals("🌧️⛈️ Rain, Thunderstorm", formatWeather("%emoji %main", data, "metric"))
    }

    @Test
    fun mapsConditionCodes() {
        fun emoji(id: Int) = weatherEmoji(WeatherDescription(id, "", "", "01d"))
        assertEquals("🌦️", emoji(300))
        assertEquals("❄️", emoji(601))
        assertEquals("🌨️", emoji(611))
        assertEquals("🌫️", emoji(741))
        assertEquals("🌪️", emoji(781))
        assertEquals("⛅", emoji(802))
        assertEquals("☁️", emoji(804))
    }
}
