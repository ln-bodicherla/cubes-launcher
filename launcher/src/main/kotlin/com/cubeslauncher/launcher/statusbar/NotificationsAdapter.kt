package com.cubeslauncher.launcher.statusbar

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.launcher.databinding.ItemNotificationBinding
import com.cubeslauncher.launcher.model.NotificationInfo

class NotificationsAdapter(
    private val onDismiss: (NotificationInfo) -> Unit,
    private val onTap: (NotificationInfo) -> Unit
) : ListAdapter<NotificationInfo, NotificationsAdapter.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NotificationInfo>() {
            override fun areItemsTheSame(oldItem: NotificationInfo, newItem: NotificationInfo) =
                oldItem.key == newItem.key

            override fun areContentsTheSame(oldItem: NotificationInfo, newItem: NotificationInfo) =
                oldItem.key == newItem.key &&
                        oldItem.title == newItem.title &&
                        oldItem.body == newItem.body
        }
    }

    inner class ViewHolder(
        private val binding: ItemNotificationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(info: NotificationInfo) {
            binding.appName.text = info.appName
            binding.notificationTitle.text = info.title
            binding.notificationBody.text = info.body

            binding.appIcon.setImageDrawable(info.appIcon)

            binding.timestamp.text = DateUtils.getRelativeTimeSpanString(
                info.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )

            binding.dismissButton.setOnClickListener { onDismiss(info) }
            binding.root.setOnClickListener { onTap(info) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNotificationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
