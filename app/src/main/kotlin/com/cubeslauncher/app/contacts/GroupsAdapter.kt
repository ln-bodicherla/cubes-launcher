package com.cubeslauncher.app.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.app.R
import com.cubeslauncher.app.contacts.model.ContactGroup
import com.cubeslauncher.app.databinding.ItemContactGroupBinding

class GroupsAdapter(
    private val groups: List<ContactGroup>,
    private val onGroupClick: (ContactGroup) -> Unit
) : RecyclerView.Adapter<GroupsAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: ItemContactGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: ContactGroup) {
            binding.groupName.text = group.title
            binding.groupMemberCount.text = binding.root.context.getString(
                R.string.contacts_group_members, group.memberCount
            )
            binding.root.setOnClickListener { onGroupClick(group) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactGroupBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(groups[position])
    }

    override fun getItemCount(): Int = groups.size
}
