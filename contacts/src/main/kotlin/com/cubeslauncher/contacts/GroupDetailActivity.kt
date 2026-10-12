package com.cubeslauncher.contacts

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.contacts.model.Contact
import com.cubeslauncher.contacts.databinding.ActivityGroupDetailBinding
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper

class GroupDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GROUP_ID = "extra_group_id"
        const val EXTRA_GROUP_TITLE = "extra_group_title"
    }

    private lateinit var binding: ActivityGroupDetailBinding
    private lateinit var repository: ContactsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityGroupDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ContactsRepository(contentResolver)

        applyWindowInsets()

        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, -1)
        val groupTitle = intent.getStringExtra(EXTRA_GROUP_TITLE) ?: ""

        binding.groupTitle.text = groupTitle
        binding.groupTitle.setTextColor(AccentColorManager.getAccentColor())

        binding.membersRecyclerView.layoutManager = LinearLayoutManager(this)

        loadMembers(groupId)
    }

    @Deprecated("Use onBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            val extraTop = (16 * density).toInt()

            binding.headerContainer.setPadding(
                binding.headerContainer.paddingLeft,
                systemBars.top + extraTop,
                binding.headerContainer.paddingRight,
                binding.headerContainer.paddingBottom
            )

            binding.membersRecyclerView.setPadding(
                binding.membersRecyclerView.paddingLeft,
                binding.membersRecyclerView.paddingTop,
                binding.membersRecyclerView.paddingRight,
                systemBars.bottom + (24 * density).toInt()
            )
            insets
        }
    }

    private fun loadMembers(groupId: Long) {
        if (groupId == -1L) {
            showEmpty()
            return
        }

        val members = repository.readGroupMembers(groupId)
        if (members.isEmpty()) {
            showEmpty()
            return
        }

        val items = buildContactItems(members)
        binding.membersRecyclerView.adapter = ContactsListAdapter(
            items = items,
            onContactClick = { contact -> openContactDetail(contact) },
            onHeaderClick = { /* no letter grid in group detail */ }
        )
        binding.emptyState.visibility = View.GONE
        binding.membersRecyclerView.visibility = View.VISIBLE
    }

    private fun buildContactItems(contacts: List<Contact>): List<ContactListItem> {
        val sorted = contacts.sortedBy { it.displayName.lowercase() }
        val items = mutableListOf<ContactListItem>()
        var currentLetter = ""

        for (contact in sorted) {
            val firstChar = contact.displayName.firstOrNull()?.uppercase() ?: "#"
            val letter = if (firstChar.first().isLetter()) firstChar else "#"

            if (letter != currentLetter) {
                currentLetter = letter
                items.add(ContactListItem.Header(letter))
            }
            items.add(ContactListItem.Entry(contact))
        }
        return items
    }

    private fun openContactDetail(contact: Contact) {
        val intent = Intent(this, ContactDetailActivity::class.java).apply {
            putExtra(ContactDetailActivity.EXTRA_CONTACT_ID, contact.id)
        }
        startActivity(intent)
    }

    private fun showEmpty() {
        binding.emptyState.visibility = View.VISIBLE
        binding.membersRecyclerView.visibility = View.GONE
    }
}
