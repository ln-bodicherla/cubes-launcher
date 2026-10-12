package com.cubeslauncher.weather.model

data class HourlyForecast(
    val time: String,
    val temperature: Double,
    val weatherCode: Int
)
