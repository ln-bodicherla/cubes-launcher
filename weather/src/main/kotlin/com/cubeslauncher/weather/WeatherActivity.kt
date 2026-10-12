package com.cubeslauncher.weather

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper
import com.cubeslauncher.weather.adapter.DailyForecastAdapter
import com.cubeslauncher.weather.adapter.HourlyForecastAdapter
import com.cubeslauncher.weather.api.WeatherApiClient
import com.cubeslauncher.weather.api.WeatherParser
import com.cubeslauncher.weather.databinding.ActivityWeatherBinding
import com.cubeslauncher.weather.model.CurrentWeather
import com.cubeslauncher.weather.model.DailyForecast
import com.cubeslauncher.weather.model.HourlyForecast
import com.cubeslauncher.weather.model.SavedLocation
import com.cubeslauncher.weather.util.WeatherIconMapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.roundToInt

class WeatherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeatherBinding
    private lateinit var prefs: WeatherPreferencesManager
    private lateinit var hourlyAdapter: HourlyForecastAdapter
    private lateinit var dailyAdapter: DailyForecastAdapter

    private var currentLocation: SavedLocation? = null
    private var currentThemeMode: String = "dark"
    private var currentAccentColor: String = "#0078D7"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        prefs = WeatherPreferencesManager(applicationContext)
        currentThemeMode = runBlocking { prefs.themeMode.first() }
        currentAccentColor = runBlocking { prefs.accentColor.first() }
        AccentColorManager.init(applicationContext)

        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        setupRecyclerViews()
        setupClickListeners()
        applyAccentColors()

        // Check if we have saved locations
        lifecycleScope.launch {
            val locations = prefs.savedLocations.first()
            if (locations.isEmpty()) {
                // First launch: go to add location
                startActivity(Intent(this@WeatherActivity, AddLocationActivity::class.java))
            } else {
                val index = prefs.selectedLocationIndex.first().coerceIn(0, locations.size - 1)
                currentLocation = locations[index]
                loadWeatherData()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val newMode = runBlocking { prefs.themeMode.first() }
        val newAccent = runBlocking { prefs.accentColor.first() }
        if (newMode != currentThemeMode || newAccent != currentAccentColor) {
            recreate()
            return
        }
        applyAccentColors()

        lifecycleScope.launch {
            val locations = prefs.savedLocations.first()
            if (locations.isNotEmpty()) {
                val index = prefs.selectedLocationIndex.first().coerceIn(0, locations.size - 1)
                val loc = locations[index]
                if (currentLocation != loc) {
                    currentLocation = loc
                    loadWeatherData()
                }
            }
        }
    }

    private fun setupRecyclerViews() {
        hourlyAdapter = HourlyForecastAdapter()
        binding.hourlyRecyclerView.apply {
            layoutManager = LinearLayoutManager(
                this@WeatherActivity, LinearLayoutManager.HORIZONTAL, false
            )
            adapter = hourlyAdapter
        }

        dailyAdapter = DailyForecastAdapter()
        binding.dailyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@WeatherActivity)
            isNestedScrollingEnabled = false
            adapter = dailyAdapter
        }
    }

    private fun setupClickListeners() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, WeatherSettingsActivity::class.java))
        }

        binding.addLocationButton.setOnClickListener {
            startActivity(Intent(this, AddLocationActivity::class.java))
        }

        binding.retryButton.setOnClickListener {
            loadWeatherData()
        }
    }

    private fun applyAccentColors() {
        val accentColor = AccentColorManager.getAccentColor()
        binding.hourlyHeader.setTextColor(accentColor)
        binding.dailyHeader.setTextColor(accentColor)
        binding.detailsHeader.setTextColor(accentColor)
    }

    private fun loadWeatherData() {
        val location = currentLocation ?: return

        showLoading()

        lifecycleScope.launch {
            try {
                val tempUnit = prefs.temperatureUnit.first()
                val windUnit = prefs.windSpeedUnit.first()

                val rawJson = WeatherApiClient.fetchWeather(
                    lat = location.latitude,
                    lon = location.longitude,
                    temperatureUnit = tempUnit,
                    windSpeedUnit = windUnit
                )

                // Cache the response
                prefs.setLastWeatherCache(rawJson)

                val current = WeatherParser.parseCurrentWeather(rawJson)
                val hourly = WeatherParser.parseHourlyForecast(rawJson)
                val daily = WeatherParser.parseDailyForecast(rawJson)

                displayWeather(location, current, hourly, daily, tempUnit, windUnit)
                showContent()
            } catch (e: Exception) {
                // Try to load from cache
                val cache = prefs.lastWeatherCache.first()
                if (cache.isNotEmpty()) {
                    try {
                        val tempUnit = prefs.temperatureUnit.first()
                        val windUnit = prefs.windSpeedUnit.first()
                        val current = WeatherParser.parseCurrentWeather(cache)
                        val hourly = WeatherParser.parseHourlyForecast(cache)
                        val daily = WeatherParser.parseDailyForecast(cache)
                        displayWeather(location, current, hourly, daily, tempUnit, windUnit)
                        showContent()
                    } catch (_: Exception) {
                        showError()
                    }
                } else {
                    showError()
                }
            }
        }
    }

    private fun displayWeather(
        location: SavedLocation,
        current: CurrentWeather,
        hourly: List<HourlyForecast>,
        daily: List<DailyForecast>,
        tempUnit: String,
        windUnit: String
    ) {
        val degSymbol = "\u00B0"

        // City name
        binding.cityNameText.text = location.name

        // Temperature
        binding.temperatureText.text = "${current.temperature.roundToInt()}$degSymbol"

        // Condition
        val conditionText = WeatherIconMapper.getConditionText(current.weatherCode)
        binding.conditionText.text = conditionText
        binding.conditionIcon.setImageResource(
            WeatherIconMapper.getIconResId(current.weatherCode)
        )

        // High / Low from daily forecast
        if (daily.isNotEmpty()) {
            val today = daily[0]
            binding.highLowText.text = getString(
                R.string.high_low_format,
                today.high.roundToInt(),
                today.low.roundToInt()
            )
        }

        // Feels like
        binding.feelsLikeText.text = getString(
            R.string.feels_like_format,
            current.feelsLike.roundToInt()
        )

        // Hourly forecast
        hourlyAdapter.submitList(hourly, degSymbol)

        // Daily forecast
        dailyAdapter.submitList(daily, degSymbol)

        // Details
        binding.humidityValue.text = getString(R.string.humidity_format, current.humidity)

        if (windUnit == "kmh") {
            binding.windValue.text = getString(R.string.wind_kmh_format, current.windSpeed)
        } else {
            binding.windValue.text = getString(R.string.wind_mph_format, current.windSpeed)
        }

        binding.uvIndexValue.text = getString(R.string.uv_index_na)
        binding.pressureValue.text = getString(R.string.pressure_format, current.pressure)
        binding.visibilityValue.text = getString(R.string.visibility_format)
        binding.dewPointValue.text = getString(R.string.dew_point_na)
    }

    private fun showLoading() {
        binding.scrollView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.loadingIndicator.visibility = View.VISIBLE
    }

    private fun showContent() {
        binding.loadingIndicator.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.scrollView.visibility = View.VISIBLE
    }

    private fun showError() {
        binding.loadingIndicator.visibility = View.GONE
        binding.scrollView.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
    }
}
