package com.cubeslauncher.app.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.ItemPinnedAppBinding
import com.cubeslauncher.app.model.AppInfo
import com.cubeslauncher.app.model.TileSize
import com.cubeslauncher.app.util.AccentColorManager

class PinnedAppsAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Unit = { _, _ -> },
    private val onResizeClick: ((AppInfo) -> Unit)? = null
) : ListAdapter<AppInfo, PinnedAppsAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.packageName == newItem.packageName
            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo) =
                oldItem.packageName == newItem.packageName && oldItem.tileSize == newItem.tileSize
        }
    }

    private var tileAlpha: Float = 1f
    private var notificationCounts: Map<String, Int> = emptyMap()
    private var tileSizes: Map<String, TileSize> = emptyMap()
    var isWiggleMode: Boolean = false
        private set
    var selectedPosition: Int = -1
        private set

    fun setTileAlpha(alpha: Float) {
        tileAlpha = alpha
        notifyDataSetChanged()
    }

    fun setNotificationCounts(counts: Map<String, Int>) {
        notificationCounts = counts
        notifyDataSetChanged()
    }

    fun setTileSizes(sizes: Map<String, TileSize>) {
        tileSizes = sizes
        notifyDataSetChanged()
    }

    fun getTileSize(packageName: String): TileSize {
        return tileSizes[packageName] ?: TileSize.SMALL
    }

    fun setWiggleMode(enabled: Boolean) {
        isWiggleMode = enabled
        selectedPosition = -1
        notifyDataSetChanged()
    }

    fun setSelectedTile(position: Int) {
        val old = selectedPosition
        selectedPosition = position
        if (old >= 0 && old < itemCount) notifyItemChanged(old)
        if (position >= 0 && position < itemCount) notifyItemChanged(position)
    }

    fun getSelectedTile(): Int = selectedPosition

    fun moveItem(fromPos: Int, toPos: Int): List<AppInfo> {
        val list = currentList.toMutableList()
        if (fromPos < 0 || fromPos >= list.size || toPos < 0 || toPos >= list.size) return list
        val item = list.removeAt(fromPos)
        list.add(toPos, item)
        // Keep selectedPosition tracking the same tile after reorder
        if (selectedPosition >= 0) {
            if (selectedPosition == fromPos) {
                selectedPosition = toPos
            } else if (fromPos < toPos && selectedPosition > fromPos && selectedPosition <= toPos) {
                selectedPosition--
            } else if (fromPos > toPos && selectedPosition >= toPos && selectedPosition < fromPos) {
                selectedPosition++
            }
        }
        submitList(list)
        return list
    }

    inner class ViewHolder(
        private val binding: ItemPinnedAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(app: AppInfo, position: Int) {
            // Fill tile with accent color
            val accentColor = AccentColorManager.getAccentColor()
            val bg = GradientDrawable()
            bg.setColor(accentColor)
            bg.cornerRadius = 0f
            bg.alpha = (tileAlpha * 255).toInt()
            binding.root.background = bg

            // Try monochrome icon (API 33+)
            val icon = app.icon
            var hasMonochrome = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && icon is AdaptiveIconDrawable) {
                val mono = icon.monochrome
                if (mono != null) {
                    binding.pinnedAppIcon.setImageDrawable(mono)
                    binding.pinnedAppIcon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
                    hasMonochrome = true
                }
            }

            // Scale icon to 125% of tile size
            binding.pinnedAppIcon.scaleX = 1.25f
            binding.pinnedAppIcon.scaleY = 1.25f

            if (hasMonochrome) {
                binding.pinnedAppIcon.visibility = View.VISIBLE
                binding.pinnedAppLetters.visibility = View.GONE
            } else {
                // No monochrome icon — show two-letter logo
                binding.pinnedAppIcon.visibility = View.GONE
                binding.pinnedAppLetters.visibility = View.VISIBLE
                val label = app.label.trim()
                val letters = when {
                    label.length >= 2 -> label.substring(0, 2)
                    label.isNotEmpty() -> label.substring(0, 1)
                    else -> "?"
                }
                binding.pinnedAppLetters.text = letters.uppercase()
            }

            // Notification badge
            val count = notificationCounts[app.packageName] ?: 0
            if (count > 0) {
                binding.notificationBadge.text = count.toString()
                binding.notificationBadge.visibility = View.VISIBLE
            } else {
                binding.notificationBadge.visibility = View.GONE
            }

            // Wiggle animation
            val view = binding.root
            val existingAnimator = view.getTag(R.id.wiggle_animator_tag) as? ObjectAnimator
            if (isWiggleMode) {
                if (existingAnimator == null || !existingAnimator.isRunning) {
                    existingAnimator?.cancel()
                    val animator = ObjectAnimator.ofFloat(view, "rotation", -1f, 1f).apply {
                        duration = 150
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        interpolator = LinearInterpolator()
                        startDelay = (position * 30L) % 150L
                    }
                    view.setTag(R.id.wiggle_animator_tag, animator)
                    animator.start()
                }
            } else {
                existingAnimator?.cancel()
                view.setTag(R.id.wiggle_animator_tag, null)
                view.rotation = 0f
            }

            // Resize icon
            val isSelected = isWiggleMode && position == selectedPosition
            binding.resizeIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.resizeIcon.setOnClickListener { onResizeClick?.invoke(app) }

            binding.root.setOnClickListener { onAppClick(app) }
            binding.root.setOnLongClickListener { onAppLongClick(app, it); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPinnedAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        val animator = holder.itemView.getTag(R.id.wiggle_animator_tag) as? ObjectAnimator
        animator?.cancel()
        holder.itemView.setTag(R.id.wiggle_animator_tag, null)
        holder.itemView.rotation = 0f
    }
}
