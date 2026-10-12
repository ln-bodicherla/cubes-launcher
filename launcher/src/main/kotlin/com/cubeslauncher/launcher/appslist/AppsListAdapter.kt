package com.cubeslauncher.launcher.appslist

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.launcher.databinding.ItemAppBinding
import com.cubeslauncher.launcher.databinding.ItemLetterHeaderBinding
import com.cubeslauncher.launcher.model.AppInfo
import com.cubeslauncher.common.theme.AccentColorManager

class AppsListAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo, View) -> Unit,
    private val onLetterClick: (String, View) -> Unit,
    private val onLetterFastScroll: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_APP = 1
    }

    private var items: List<AppItem> = emptyList()
    private var headerLetters: List<String> = emptyList()

    fun submitList(newItems: List<AppItem>) {
        items = newItems
        headerLetters = items.filterIsInstance<AppItem.Header>().map { it.letter }
        notifyDataSetChanged()
    }

    fun getItemAt(position: Int): AppItem? =
        items.getOrNull(position)

    fun getFirstApp(): AppInfo? =
        items.filterIsInstance<AppItem.App>().firstOrNull()?.info

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is AppItem.Header -> TYPE_HEADER
        is AppItem.App -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                ItemLetterHeaderBinding.inflate(inflater, parent, false)
            )
            TYPE_APP -> AppViewHolder(
                ItemAppBinding.inflate(inflater, parent, false)
            )
            else -> throw IllegalArgumentException("Unknown view type $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AppItem.Header -> (holder as HeaderViewHolder).bind(item)
            is AppItem.App -> (holder as AppViewHolder).bind(item.info)
        }
    }

    inner class HeaderViewHolder(
        private val binding: ItemLetterHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isDragging = false
        private var dragStartRawY = 0f
        private var dragStartLetterIndex = 0
        private var lastScrolledLetter = ""

        @SuppressLint("ClickableViewAccessibility")
        fun bind(header: AppItem.Header) {
            // Reset visibility — StickyHeaderDecoration may have set it INVISIBLE
            itemView.visibility = View.VISIBLE
            binding.letterHeader.text = header.letter
            binding.letterHeaderTile.setBackgroundColor(AccentColorManager.getAccentColor())

            // Remove click/longClick listeners — all handled in touch listener
            binding.root.setOnClickListener(null)
            binding.root.setOnLongClickListener(null)

            binding.root.setOnTouchListener { v, event ->
                val touchSlop = ViewConfiguration.get(v.context).scaledTouchSlop

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartRawY = event.rawY
                        isDragging = false
                        lastScrolledLetter = header.letter
                        dragStartLetterIndex = headerLetters.indexOf(header.letter)
                            .coerceAtLeast(0)

                        // Claim touch immediately so RecyclerView doesn't steal it
                        v.parent?.requestDisallowInterceptTouchEvent(true)

                        // Tilt effect
                        val cx = v.width / 2f
                        val cy = v.height / 2f
                        v.pivotX = cx
                        v.pivotY = cy
                        v.rotationX = -(event.y - cy) / cy * 4f
                        v.rotationY = (event.x - cx) / cx * 4f
                        v.scaleX = 0.97f
                        v.scaleY = 0.97f
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - dragStartRawY
                        if (!isDragging && Math.abs(dy) > touchSlop) {
                            isDragging = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        }
                        if (isDragging) {
                            val stepSize = v.height.coerceAtLeast(1) * 1.2f
                            val letterOffset = (dy / stepSize).toInt()
                            val targetIndex = (dragStartLetterIndex + letterOffset)
                                .coerceIn(0, headerLetters.size - 1)
                            val targetLetter = headerLetters[targetIndex]
                            if (targetLetter != lastScrolledLetter) {
                                lastScrolledLetter = targetLetter
                                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onLetterFastScroll(targetLetter)
                            }
                        }
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_UP -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        v.animate()
                            .rotationX(0f).rotationY(0f)
                            .scaleX(1f).scaleY(1f)
                            .setDuration(150).start()

                        if (!isDragging) {
                            // It was a tap — show letter grid popup
                            onLetterClick(header.letter, v)
                        }
                        isDragging = false
                        return@setOnTouchListener true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        v.animate()
                            .rotationX(0f).rotationY(0f)
                            .scaleX(1f).scaleY(1f)
                            .setDuration(150).start()
                        return@setOnTouchListener true
                    }
                }
                false
            }
        }
    }

    inner class AppViewHolder(
        private val binding: ItemAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppInfo) {
            binding.appIcon.setImageDrawable(app.icon)
            binding.appName.text = app.label
            binding.root.setOnClickListener { onAppClick(app) }
            binding.root.setOnLongClickListener { view ->
                onAppLongClick(app, view)
                true
            }
            applyTiltEffect(binding.root)
        }
    }

    /**
     * WP10-style tilt effect: the item tilts in 3D toward the touch point
     * on press, and resets on release.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun applyTiltEffect(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val cx = v.width / 2f
                    val cy = v.height / 2f
                    // Tilt toward touch point
                    val rotX = -(event.y - cy) / cy * 4f  // max 4 degrees
                    val rotY = (event.x - cx) / cx * 4f
                    v.pivotX = cx
                    v.pivotY = cy
                    v.rotationX = rotX
                    v.rotationY = rotY
                    v.scaleX = 0.97f
                    v.scaleY = 0.97f
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .rotationX(0f)
                        .rotationY(0f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150)
                        .start()
                }
            }
            false // don't consume — let click/longClick still fire
        }
    }
}
