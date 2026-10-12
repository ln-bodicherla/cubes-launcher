package com.cubeslauncher.weather.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.weather.databinding.ItemHourlyForecastBinding
import com.cubeslauncher.weather.model.HourlyForecast
import com.cubeslauncher.weather.util.WeatherIconMapper
import kotlin.math.roundToInt

class HourlyForecastAdapter : RecyclerView.Adapter<HourlyForecastAdapter.ViewHolder>() {

    private var items: List<HourlyForecast> = emptyList()
    private var unitSymbol: String = "\u00B0"

    fun submitList(forecasts: List<HourlyForecast>, symbol: String = "\u00B0") {
        items = forecasts
        unitSymbol = symbol
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHourlyForecastBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemHourlyForecastBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(forecast: HourlyForecast) {
            binding.timeText.text = forecast.time
            binding.temperatureText.text = "${forecast.temperature.roundToInt()}$unitSymbol"
            binding.weatherIcon.setImageResource(
                WeatherIconMapper.getIconResId(forecast.weatherCode)
            )
        }
    }
}
