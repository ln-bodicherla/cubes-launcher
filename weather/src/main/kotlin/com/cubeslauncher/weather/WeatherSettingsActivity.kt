package com.cubeslauncher.weather

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper
import com.cubeslauncher.weather.databinding.ActivityWeatherSettingsBinding
import com.cubeslauncher.weather.model.SavedLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WeatherSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeatherSettingsBinding
    private lateinit var prefs: WeatherPreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        AccentColorManager.init(this)

        binding = ActivityWeatherSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        prefs = WeatherPreferencesManager(applicationContext)

        setupClickListeners()
        loadCurrentSettings()
    }

    override fun onResume() {
        super.onResume()
        loadCurrentSettings()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener { finish() }

        binding.btnFahrenheit.setOnClickListener {
            lifecycleScope.launch {
                prefs.setTemperatureUnit("fahrenheit")
                updateTemperatureToggle("fahrenheit")
            }
        }

        binding.btnCelsius.setOnClickListener {
            lifecycleScope.launch {
                prefs.setTemperatureUnit("celsius")
                updateTemperatureToggle("celsius")
            }
        }

        binding.btnMph.setOnClickListener {
            lifecycleScope.launch {
                prefs.setWindSpeedUnit("mph")
                updateWindSpeedToggle("mph")
            }
        }

        binding.btnKmh.setOnClickListener {
            lifecycleScope.launch {
                prefs.setWindSpeedUnit("kmh")
                updateWindSpeedToggle("kmh")
            }
        }
    }

    private fun loadCurrentSettings() {
        lifecycleScope.launch {
            val tempUnit = prefs.temperatureUnit.first()
            val windUnit = prefs.windSpeedUnit.first()
            val locations = prefs.savedLocations.first()

            updateTemperatureToggle(tempUnit)
            updateWindSpeedToggle(windUnit)
            displayLocations(locations)
        }
    }

    private fun updateTemperatureToggle(selected: String) {
        val accentColor = AccentColorManager.getAccentColor()
        val surfaceColor = resolveAttrColor(com.cubeslauncher.common.R.attr.wpSurface)
        val textColor = resolveAttrColor(com.cubeslauncher.common.R.attr.wpTextPrimary)

        if (selected == "fahrenheit") {
            binding.btnFahrenheit.setBackgroundColor(accentColor)
            binding.btnFahrenheit.setTextColor(Color.WHITE)
            binding.btnCelsius.setBackgroundColor(surfaceColor)
            binding.btnCelsius.setTextColor(textColor)
        } else {
            binding.btnCelsius.setBackgroundColor(accentColor)
            binding.btnCelsius.setTextColor(Color.WHITE)
            binding.btnFahrenheit.setBackgroundColor(surfaceColor)
            binding.btnFahrenheit.setTextColor(textColor)
        }
    }

    private fun updateWindSpeedToggle(selected: String) {
        val accentColor = AccentColorManager.getAccentColor()
        val surfaceColor = resolveAttrColor(com.cubeslauncher.common.R.attr.wpSurface)
        val textColor = resolveAttrColor(com.cubeslauncher.common.R.attr.wpTextPrimary)

        if (selected == "mph") {
            binding.btnMph.setBackgroundColor(accentColor)
            binding.btnMph.setTextColor(Color.WHITE)
            binding.btnKmh.setBackgroundColor(surfaceColor)
            binding.btnKmh.setTextColor(textColor)
        } else {
            binding.btnKmh.setBackgroundColor(accentColor)
            binding.btnKmh.setTextColor(Color.WHITE)
            binding.btnMph.setBackgroundColor(surfaceColor)
            binding.btnMph.setTextColor(textColor)
        }
    }

    private fun displayLocations(locations: List<SavedLocation>) {
        binding.locationsContainer.removeAllViews()

        if (locations.isEmpty()) {
            binding.noLocationsText.visibility = View.VISIBLE
            return
        }

        binding.noLocationsText.visibility = View.GONE

        for ((index, location) in locations.withIndex()) {
            val row = createLocationRow(location, index)
            binding.locationsContainer.addView(row)
        }
    }

    private fun createLocationRow(location: SavedLocation, index: Int): View {
        val textColor = resolveAttrColor(com.cubeslauncher.common.R.attr.wpTextPrimary)
        val secondaryColor = resolveAttrColor(com.cubeslauncher.common.R.attr.wpTextSecondary)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(56)
            )
        }

        val nameContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }

        val nameText = TextView(this).apply {
            text = location.name
            setTextColor(textColor)
            textSize = 16f
            typeface = resources.getFont(com.cubeslauncher.common.R.font.selawik_regular)
        }

        val detailText = TextView(this).apply {
            val detail = buildString {
                if (location.admin.isNotEmpty()) append(location.admin)
                if (location.country.isNotEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append(location.country)
                }
            }
            text = detail
            setTextColor(secondaryColor)
            textSize = 13f
            typeface = resources.getFont(com.cubeslauncher.common.R.font.selawik_regular)
        }

        nameContainer.addView(nameText)
        if (location.admin.isNotEmpty() || location.country.isNotEmpty()) {
            nameContainer.addView(detailText)
        }

        val deleteButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_weather_delete)
            layoutParams = LinearLayout.LayoutParams(dpToPx(48), dpToPx(48))
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                lifecycleScope.launch {
                    prefs.removeLocation(index)
                    val updated = prefs.savedLocations.first()
                    displayLocations(updated)
                }
            }
        }

        row.addView(nameContainer)
        row.addView(deleteButton)

        return row
    }

    private fun resolveAttrColor(attr: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics
        ).toInt()
    }
}
