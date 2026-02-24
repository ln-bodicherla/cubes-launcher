package com.cubeslauncher.app.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.app.contacts.model.Contact
import com.cubeslauncher.app.databinding.ItemContactEntryBinding
import com.cubeslauncher.app.databinding.ItemContactHeaderBinding
import com.cubeslauncher.app.util.AccentColorManager
import android.graphics.drawable.GradientDrawable

sealed class ContactListItem {
    data class Header(val letter: String) : ContactListItem()
    data class Entry(val contact: Contact) : ContactListItem()
}

class ContactsListAdapter(
    private var items: List<ContactListItem>,
    private val onContactClick: (Contact) -> Unit,
    private val onHeaderClick: (String) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
    }

    fun updateItems(newItems: List<ContactListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ContactListItem.Header -> TYPE_HEADER
        is ContactListItem.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val binding = ItemContactHeaderBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                HeaderViewHolder(binding)
            }
            else -> {
                val binding = ItemContactEntryBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                EntryViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ContactListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ContactListItem.Entry -> (holder as EntryViewHolder).bind(item.contact)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class HeaderViewHolder(
        private val binding: ItemContactHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(header: ContactListItem.Header) {
            binding.headerLetter.text = header.letter
            binding.headerLetter.setTextColor(AccentColorManager.getAccentColor())
            binding.root.setOnClickListener { onHeaderClick(header.letter) }
        }
    }

    inner class EntryViewHolder(
        private val binding: ItemContactEntryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.contactName.text = contact.displayName
            binding.contactInitials.text = getInitials(contact.displayName)
            (binding.contactInitials.background as? GradientDrawable)?.setColor(AccentColorManager.getAccentColor())
            binding.contactNumber.text = contact.phones.firstOrNull()?.number ?: ""
            binding.root.setOnClickListener { onContactClick(contact) }
        }

        private fun getInitials(name: String): String {
            val parts = name.trim().split("\\s+".toRegex())
            return when {
                parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
                parts.isNotEmpty() && parts[0].isNotEmpty() -> parts[0].first().uppercase()
                else -> "?"
            }
        }
    }
}
