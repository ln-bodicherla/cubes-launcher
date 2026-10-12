package com.cubeslauncher.weather.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WeatherApiClient {

    private const val WEATHER_BASE_URL = "https://api.open-meteo.com/v1/forecast"
    private const val GEOCODING_BASE_URL = "https://geocoding-api.open-meteo.com/v1/search"
    private const val CONNECT_TIMEOUT = 10000
    private const val READ_TIMEOUT = 15000

    suspend fun fetchWeather(
        lat: Double,
        lon: Double,
        temperatureUnit: String = "fahrenheit",
        windSpeedUnit: String = "mph"
    ): String = withContext(Dispatchers.IO) {
        val url = buildString {
            append(WEATHER_BASE_URL)
            append("?latitude=$lat")
            append("&longitude=$lon")
            append("&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,surface_pressure")
            append("&hourly=temperature_2m,weather_code")
            append("&daily=temperature_2m_max,temperature_2m_min,weather_code")
            append("&temperature_unit=$temperatureUnit")
            append("&wind_speed_unit=$windSpeedUnit")
            append("&timezone=auto")
            append("&forecast_days=5")
        }
        performGet(url)
    }

    suspend fun searchCities(query: String): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$GEOCODING_BASE_URL?name=$encoded&count=10"
        performGet(url)
    }

    private fun performGet(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream
                val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw WeatherApiException("HTTP $responseCode: $errorBody")
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            return reader.use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

class WeatherApiException(message: String) : Exception(message)
