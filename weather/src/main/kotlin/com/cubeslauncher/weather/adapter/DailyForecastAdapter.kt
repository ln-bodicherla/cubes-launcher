package com.cubeslauncher.weather.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.weather.databinding.ItemDailyForecastBinding
import com.cubeslauncher.weather.model.DailyForecast
import com.cubeslauncher.weather.util.WeatherIconMapper
import kotlin.math.roundToInt

class DailyForecastAdapter : RecyclerView.Adapter<DailyForecastAdapter.ViewHolder>() {

    private var items: List<DailyForecast> = emptyList()
    private var unitSymbol: String = "\u00B0"

    fun submitList(forecasts: List<DailyForecast>, symbol: String = "\u00B0") {
        items = forecasts
        unitSymbol = symbol
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDailyForecastBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        private val binding: ItemDailyForecastBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(forecast: DailyForecast) {
            binding.dayNameText.text = forecast.dayName
            binding.weatherIcon.setImageResource(
                WeatherIconMapper.getIconResId(forecast.weatherCode)
            )
            binding.highTempText.text = "${forecast.high.roundToInt()}$unitSymbol"
            binding.lowTempText.text = "${forecast.low.roundToInt()}$unitSymbol"
        }
    }
}
