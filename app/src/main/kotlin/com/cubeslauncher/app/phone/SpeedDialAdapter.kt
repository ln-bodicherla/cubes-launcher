package com.cubeslauncher.app.phone

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.app.databinding.ItemPhoneSpeedDialBinding
import com.cubeslauncher.app.phone.model.SpeedDialContact

class SpeedDialAdapter(
    private var contacts: List<SpeedDialContact>,
    private val onContactClick: (SpeedDialContact) -> Unit
) : RecyclerView.Adapter<SpeedDialAdapter.ViewHolder>() {

    fun updateContacts(newContacts: List<SpeedDialContact>) {
        contacts = newContacts
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val binding: ItemPhoneSpeedDialBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contact: SpeedDialContact) {
            binding.contactName.text = contact.name
            binding.contactInitials.text = getInitials(contact.name)
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
        val binding = ItemPhoneSpeedDialBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(contacts[position])
    }

    override fun getItemCount(): Int = contacts.size
}
