package com.cubeslauncher.clock.stopwatch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.clock.databinding.ItemLapBinding

class LapsAdapter(
    private val laps: List<StopwatchFragment.Lap>
) : RecyclerView.Adapter<LapsAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: ItemLapBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(lap: StopwatchFragment.Lap) {
            binding.lapNumber.text = lap.number.toString()
            binding.lapTime.text = StopwatchFragment.formatTime(lap.lapTime)
            binding.totalTime.text = StopwatchFragment.formatTime(lap.totalTime)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLapBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(laps[position])
    }

    override fun getItemCount(): Int = laps.size
}
