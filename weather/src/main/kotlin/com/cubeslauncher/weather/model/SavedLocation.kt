package com.cubeslauncher.weather.model

data class SavedLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val admin: String = ""
)
