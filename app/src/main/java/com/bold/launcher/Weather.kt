package com.bold.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Weather {
    private const val PREFS = "weather"

    fun cached(c: Context): String = c.getSharedPreferences(PREFS, 0).getString("text", "") ?: ""

    /** Fetches current conditions from Open-Meteo and calls onResult on the main thread. */
    fun fetch(c: Context, onResult: (String) -> Unit) {
        val app = c.applicationContext
        Thread {
            try {
                val unit = if (Config.CELSIUS) "celsius" else "fahrenheit"
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=${Config.WEATHER_LAT}" +
                        "&longitude=${Config.WEATHER_LON}&current=temperature_2m,weather_code" +
                        "&temperature_unit=$unit&timezone=auto"
                )
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val cur = JSONObject(body).getJSONObject("current")
                val temp = Math.round(cur.getDouble("temperature_2m")).toInt()
                val text = "$temp° ${describe(cur.getInt("weather_code"))}".trim()
                app.getSharedPreferences(PREFS, 0).edit().putString("text", text).apply()
                Handler(Looper.getMainLooper()).post { onResult(text) }
            } catch (_: Exception) {
                // Offline or API hiccup: keep showing the cached value
            }
        }.start()
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorm"
        else -> ""
    }
}
