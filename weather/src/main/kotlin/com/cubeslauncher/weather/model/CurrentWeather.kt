package com.cubeslauncher.weather.model

data class CurrentWeather(
    val temperature: Double,
    val feelsLike: Double,
    val weatherCode: Int,
    val humidity: Int,
    val windSpeed: Double,
    val pressure: Double
)
