package com.cubeslauncher.weather.util

object UnitConverter {
    fun celsiusToFahrenheit(c: Double): Double = c * 9.0 / 5.0 + 32
    fun fahrenheitToCelsius(f: Double): Double = (f - 32) * 5.0 / 9.0
    fun msToMph(ms: Double): Double = ms * 2.237
    fun msToKmh(ms: Double): Double = ms * 3.6
    fun mphToKmh(mph: Double): Double = mph * 1.609
    fun kmhToMph(kmh: Double): Double = kmh / 1.609
}
