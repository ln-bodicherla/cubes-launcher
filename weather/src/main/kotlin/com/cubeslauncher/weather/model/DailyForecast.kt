package com.cubeslauncher.weather.model

data class DailyForecast(
    val dayName: String,
    val high: Double,
    val low: Double,
    val weatherCode: Int
)
