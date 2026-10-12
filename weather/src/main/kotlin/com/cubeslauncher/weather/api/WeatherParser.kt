package com.cubeslauncher.weather.api

import com.cubeslauncher.weather.model.CurrentWeather
import com.cubeslauncher.weather.model.DailyForecast
import com.cubeslauncher.weather.model.HourlyForecast
import com.cubeslauncher.weather.model.SavedLocation
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object WeatherParser {

    fun parseCurrentWeather(json: String): CurrentWeather {
        val root = JSONObject(json)
        val current = root.getJSONObject("current")
        return CurrentWeather(
            temperature = current.getDouble("temperature_2m"),
            feelsLike = current.getDouble("apparent_temperature"),
            weatherCode = current.getInt("weather_code"),
            humidity = current.getInt("relative_humidity_2m"),
            windSpeed = current.getDouble("wind_speed_10m"),
            pressure = current.getDouble("surface_pressure")
        )
    }

    fun parseHourlyForecast(json: String): List<HourlyForecast> {
        val root = JSONObject(json)
        val hourly = root.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")

        val timezone = root.optString("timezone", "UTC")
        val tz = TimeZone.getTimeZone(timezone)
        val calendar = Calendar.getInstance(tz)
        val currentHourIndex = findCurrentHourIndex(times, tz)

        val forecasts = mutableListOf<HourlyForecast>()
        val startIndex = if (currentHourIndex >= 0) currentHourIndex else 0
        val endIndex = minOf(startIndex + 24, times.length())

        for (i in startIndex until endIndex) {
            val timeStr = times.getString(i)
            val formattedTime = formatHourlyTime(timeStr, tz, i == startIndex)
            forecasts.add(
                HourlyForecast(
                    time = formattedTime,
                    temperature = temps.getDouble(i),
                    weatherCode = codes.getInt(i)
                )
            )
        }
        return forecasts
    }

    fun parseDailyForecast(json: String): List<DailyForecast> {
        val root = JSONObject(json)
        val daily = root.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val maxTemps = daily.getJSONArray("temperature_2m_max")
        val minTemps = daily.getJSONArray("temperature_2m_min")
        val codes = daily.getJSONArray("weather_code")

        val forecasts = mutableListOf<DailyForecast>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayFormat = SimpleDateFormat("EEE", Locale.US)

        for (i in 0 until times.length()) {
            val dateStr = times.getString(i)
            val date = dateFormat.parse(dateStr) ?: Date()
            val dayName = if (i == 0) "Today" else dayFormat.format(date)
            forecasts.add(
                DailyForecast(
                    dayName = dayName,
                    high = maxTemps.getDouble(i),
                    low = minTemps.getDouble(i),
                    weatherCode = codes.getInt(i)
                )
            )
        }
        return forecasts
    }

    fun parseGeocodingResults(json: String): List<SavedLocation> {
        val root = JSONObject(json)
        if (!root.has("results")) return emptyList()
        val results = root.getJSONArray("results")
        val locations = mutableListOf<SavedLocation>()
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            locations.add(
                SavedLocation(
                    name = item.getString("name"),
                    latitude = item.getDouble("latitude"),
                    longitude = item.getDouble("longitude"),
                    country = item.optString("country", ""),
                    admin = item.optString("admin1", "")
                )
            )
        }
        return locations
    }

    private fun findCurrentHourIndex(times: org.json.JSONArray, tz: TimeZone): Int {
        val now = Calendar.getInstance(tz)
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        dateFormat.timeZone = tz
        val todayStr = dateFormat.format(now.time)

        for (i in 0 until times.length()) {
            val timeStr = times.getString(i)
            if (timeStr.startsWith(todayStr)) {
                val hour = timeStr.substring(11, 13).toIntOrNull() ?: continue
                if (hour >= currentHour) return i
            }
        }
        return 0
    }

    private fun formatHourlyTime(isoTime: String, tz: TimeZone, isFirst: Boolean): String {
        if (isFirst) return "Now"
        return try {
            val hour = isoTime.substring(11, 13).toIntOrNull() ?: return isoTime
            when {
                hour == 0 -> "12AM"
                hour < 12 -> "${hour}AM"
                hour == 12 -> "12PM"
                else -> "${hour - 12}PM"
            }
        } catch (e: Exception) {
            isoTime
        }
    }
}
