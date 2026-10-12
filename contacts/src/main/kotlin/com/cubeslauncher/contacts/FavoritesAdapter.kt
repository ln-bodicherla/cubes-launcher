package com.cubeslauncher.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.contacts.model.Contact
import com.cubeslauncher.contacts.databinding.ItemContactFavoriteBinding
import com.cubeslauncher.common.theme.AccentColorManager

class FavoritesAdapter(
    private var contacts: List<Contact>,
    private val onContactClick: (Contact) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.ViewHolder>() {

    fun updateContacts(newContacts: List<Contact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val binding: ItemContactFavoriteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: Contact) {
            binding.contactName.text = contact.displayName
            binding.contactInitials.text = getInitials(contact.displayName)
            binding.contactInitials.background.setTint(AccentColorManager.getAccentColor())
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactFavoriteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount(): Int = contacts.size
}
