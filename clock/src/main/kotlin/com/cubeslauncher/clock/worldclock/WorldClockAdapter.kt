package com.cubeslauncher.clock.worldclock

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.clock.databinding.ItemWorldClockBinding
import com.cubeslauncher.clock.model.WorldClockCity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class WorldClockAdapter(
    private val cities: List<WorldClockCity>,
    private val onCityLongClick: (WorldClockCity) -> Unit
) : RecyclerView.Adapter<WorldClockAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: ItemWorldClockBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(city: WorldClockCity) {
            binding.cityName.text = city.cityName

            val tz = TimeZone.getTimeZone(city.timeZoneId)
            val now = Date()

            // Calculate UTC offset
            val offsetMs = tz.getOffset(now.time)
            val offsetHours = offsetMs / (1000 * 60 * 60)
            val offsetMinutes = Math.abs((offsetMs / (1000 * 60)) % 60)
            val offsetText = if (offsetMinutes == 0) {
                String.format(Locale.getDefault(), "UTC%+d", offsetHours)
            } else {
                String.format(Locale.getDefault(), "UTC%+d:%02d", offsetHours, offsetMinutes)
            }
            binding.cityOffset.text = offsetText

            // Current time in that timezone
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.timeZone = tz
            binding.cityTime.text = sdf.format(now)

            binding.root.setOnLongClickListener {
                onCityLongClick(city)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWorldClockBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(cities[position])
    }

    override fun getItemCount(): Int = cities.size
}
