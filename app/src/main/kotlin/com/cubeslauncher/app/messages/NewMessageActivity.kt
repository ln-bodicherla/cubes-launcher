package com.cubeslauncher.app.messages

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.app.R
import com.cubeslauncher.app.contacts.ContactsRepository
import com.cubeslauncher.app.contacts.model.Contact
import com.cubeslauncher.app.databinding.ActivityNewMessageBinding
import com.cubeslauncher.app.util.AccentColorManager
import com.cubeslauncher.app.util.PreferencesManager
import com.cubeslauncher.app.util.ThemeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class NewMessageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewMessageBinding
    private lateinit var repository: MessagesRepository
    private lateinit var contactsRepository: ContactsRepository
    private var currentThemeMode: String = "dark"
    private var allContacts: List<Contact> = emptyList()
    private lateinit var suggestionsAdapter: ContactSuggestionsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        currentThemeMode = runBlocking { PreferencesManager(applicationContext).themeMode.first() }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityNewMessageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = MessagesRepository(this)
        contactsRepository = ContactsRepository(contentResolver)

        setupHeader()
        setupToField()
        setupComposeBar()
        applyWindowInsets()
        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        val newMode = runBlocking { PreferencesManager(applicationContext).themeMode.first() }
        if (newMode != currentThemeMode) {
            recreate()
        }
    }

    private fun setupHeader() {
        binding.backButton.setOnClickListener { finish() }
    }

    private fun setupToField() {
        suggestionsAdapter = ContactSuggestionsAdapter { contact ->
            val phone = contact.phones.firstOrNull()?.number ?: return@ContactSuggestionsAdapter
            binding.toField.setText(phone)
            binding.suggestionsRecycler.visibility = View.GONE
            binding.spacer.visibility = View.VISIBLE
            binding.composeText.requestFocus()
        }

        binding.suggestionsRecycler.apply {
            layoutManager = LinearLayoutManager(this@NewMessageActivity)
            adapter = suggestionsAdapter
        }

        binding.toField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    filterContacts(query)
                } else {
                    binding.suggestionsRecycler.visibility = View.GONE
                    binding.spacer.visibility = View.VISIBLE
                }
            }
        })
    }

    private fun setupComposeBar() {
        val accentColor = AccentColorManager.getAccentColor()
        binding.sendButton.setColorFilter(accentColor)

        binding.sendButton.setOnClickListener {
            val address = binding.toField.text?.toString()?.trim() ?: return@setOnClickListener
            val body = binding.composeText.text?.toString()?.trim() ?: return@setOnClickListener
            if (address.isEmpty() || body.isEmpty()) return@setOnClickListener

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    repository.sendSms(address, body)
                }

                // Open conversation
                val intent = Intent(this@NewMessageActivity, ConversationActivity::class.java).apply {
                    putExtra(ConversationActivity.EXTRA_ADDRESS, address)
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.header) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(view.paddingLeft, topInset + 16, view.paddingRight, view.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeInset = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val systemBarInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val bottomInset = maxOf(imeInset, systemBarInset)

            binding.composeBar.setPadding(
                binding.composeBar.paddingLeft,
                binding.composeBar.paddingTop,
                binding.composeBar.paddingRight,
                if (imeInset > 0) 8 else systemBarInset + 8
            )

            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)
            insets
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            allContacts = withContext(Dispatchers.IO) {
                contactsRepository.readAllContacts()
            }
        }
    }

    private fun filterContacts(query: String) {
        val lower = query.lowercase()
        val filtered = allContacts.filter { contact ->
            contact.displayName.lowercase().contains(lower) ||
            contact.phones.any { it.number.replace("[^\\d]".toRegex(), "").contains(lower.replace("[^\\d]".toRegex(), "")) }
        }.take(10)

        if (filtered.isNotEmpty()) {
            suggestionsAdapter.submitList(filtered)
            binding.suggestionsRecycler.visibility = View.VISIBLE
            binding.spacer.visibility = View.GONE
        } else {
            binding.suggestionsRecycler.visibility = View.GONE
            binding.spacer.visibility = View.VISIBLE
        }
    }

    private inner class ContactSuggestionsAdapter(
        private val onClick: (Contact) -> Unit
    ) : RecyclerView.Adapter<ContactSuggestionsAdapter.ViewHolder>() {

        private var contacts: List<Contact> = emptyList()

        fun submitList(list: List<Contact>) {
            contacts = list
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val name: TextView = itemView.findViewById(android.R.id.text1)
            val number: TextView = itemView.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            // Style to match theme
            val text1 = view.findViewById<TextView>(android.R.id.text1)
            val text2 = view.findViewById<TextView>(android.R.id.text2)
            val ta = parent.context.obtainStyledAttributes(intArrayOf(R.attr.wpTextPrimary, R.attr.wpTextSecondary))
            text1.setTextColor(ta.getColor(0, 0))
            text2.setTextColor(ta.getColor(1, 0))
            ta.recycle()
            view.setBackgroundColor(0) // Transparent
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val contact = contacts[position]
            holder.name.text = contact.displayName
            holder.number.text = contact.phones.firstOrNull()?.number ?: ""
            holder.itemView.setOnClickListener { onClick(contact) }
        }

        override fun getItemCount(): Int = contacts.size
    }
}
