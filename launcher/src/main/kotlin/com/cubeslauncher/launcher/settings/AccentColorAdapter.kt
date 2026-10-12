package com.cubeslauncher.launcher.settings

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.launcher.databinding.ItemAccentColorBinding

class AccentColorAdapter(
    private val colors: List<String>,
    private var selectedHex: String,
    private val onColorSelected: (String) -> Unit
) : RecyclerView.Adapter<AccentColorAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: ItemAccentColorBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(hex: String) {
            val color = try { Color.parseColor(hex) } catch (_: Exception) { Color.GRAY }
            binding.colorSwatch.setBackgroundColor(color)
            binding.checkMark.visibility =
                if (hex.equals(selectedHex, ignoreCase = true)) View.VISIBLE else View.GONE

            binding.root.setOnClickListener {
                val old = selectedHex
                selectedHex = hex
                // Refresh old and new
                val oldPos = colors.indexOfFirst { it.equals(old, ignoreCase = true) }
                if (oldPos >= 0) notifyItemChanged(oldPos)
                notifyItemChanged(bindingAdapterPosition)
                onColorSelected(hex)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccentColorBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(colors[position])
    }

    override fun getItemCount(): Int = colors.size
}
