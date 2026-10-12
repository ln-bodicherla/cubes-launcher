package com.cubeslauncher.contacts

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.contacts.R
import com.cubeslauncher.contacts.model.Contact
import com.cubeslauncher.contacts.databinding.FragmentContactsListBinding
import com.cubeslauncher.common.theme.AccentColorManager

class ContactsListFragment : Fragment() {

    private var _binding: FragmentContactsListBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ContactsRepository
    private lateinit var adapter: ContactsListAdapter
    private var allContacts: List<Contact> = emptyList()
    private var fullItemsList: List<ContactListItem> = emptyList()
    private var activeLetters: Set<String> = emptySet()
    private var isLetterGridVisible = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = ContactsRepository(requireContext().contentResolver)
        binding.contactsRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ContactsListAdapter(
            items = emptyList(),
            onContactClick = { contact -> openContactDetail(contact) },
            onHeaderClick = { showLetterGrid() }
        )
        binding.contactsRecyclerView.adapter = adapter

        binding.addContactButton.setBackgroundColor(AccentColorManager.getAccentColor())
        binding.addContactButton.setOnClickListener {
            startActivity(Intent(requireContext(), ContactEditActivity::class.java))
        }

        binding.letterGridOverlay.setOnClickListener { hideLetterGrid() }

        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterContacts(s?.toString().orEmpty())
            }
        })

        loadContacts()
    }

    override fun onResume() {
        super.onResume()
        loadContacts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadContacts() {
        val activity = requireActivity()
        if (activity is ContactsActivity && !activity.hasPermission(Manifest.permission.READ_CONTACTS)) {
            showEmpty()
            return
        }

        allContacts = repository.readAllContacts()
        if (allContacts.isEmpty()) {
            showEmpty()
            return
        }

        fullItemsList = buildContactItems(allContacts)
        activeLetters = fullItemsList
            .filterIsInstance<ContactListItem.Header>()
            .map { it.letter }
            .toSet()

        val query = _binding?.searchEditText?.text?.toString().orEmpty()
        if (query.isBlank()) {
            adapter.updateItems(fullItemsList)
        } else {
            filterContacts(query)
        }
        binding.emptyState.visibility = View.GONE
        binding.contactsRecyclerView.visibility = View.VISIBLE
    }

    private fun buildContactItems(contacts: List<Contact>): List<ContactListItem> {
        val items = mutableListOf<ContactListItem>()
        var currentLetter = ""

        for (contact in contacts) {
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

    private fun filterContacts(query: String) {
        if (query.isBlank()) {
            adapter.updateItems(fullItemsList)
            return
        }
        val filtered = allContacts.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.phones.any { p -> p.number.contains(query) }
        }
        adapter.updateItems(buildContactItems(filtered))
    }

    private fun showEmpty() {
        binding.emptyState.visibility = View.VISIBLE
        binding.contactsRecyclerView.visibility = View.GONE
    }

    private fun openContactDetail(contact: Contact) {
        val intent = Intent(requireContext(), ContactDetailActivity::class.java).apply {
            putExtra(ContactDetailActivity.EXTRA_CONTACT_ID, contact.id)
        }
        startActivity(intent)
    }

    private fun showLetterGrid() {
        if (isLetterGridVisible) return
        isLetterGridVisible = true
        buildLetterGrid()
        binding.letterGridOverlay.visibility = View.VISIBLE
    }

    private fun hideLetterGrid() {
        isLetterGridVisible = false
        binding.letterGridOverlay.visibility = View.GONE
    }

    private fun buildLetterGrid() {
        val grid = binding.letterGrid
        grid.removeAllViews()

        val letters = listOf("#") + ('A'..'Z').map { it.toString() }
        val cellSize = resources.getDimensionPixelSize(R.dimen.letter_grid_cell_size)
        val spacing = resources.getDimensionPixelSize(R.dimen.letter_grid_spacing)
        val textSize = resources.getDimension(R.dimen.letter_grid_text_size)

        val accentColor = AccentColorManager.getAccentColor()
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.wp_white_10)
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.wp_white)
        val dimColor = ContextCompat.getColor(requireContext(), R.color.wp_white_30)

        for ((index, letter) in letters.withIndex()) {
            val isActive = activeLetters.contains(letter)

            val cell = TextView(requireContext()).apply {
                text = letter
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize)
                typeface = resources.getFont(R.font.selawik_semibold)
                setTextColor(if (isActive) whiteColor else dimColor)
                setBackgroundColor(if (isActive) accentColor else inactiveColor)

                if (isActive) {
                    setOnClickListener {
                        scrollToLetter(letter)
                        hideLetterGrid()
                    }
                }
            }

            val params = GridLayout.LayoutParams().apply {
                width = cellSize
                height = cellSize
                columnSpec = GridLayout.spec(index % 5)
                rowSpec = GridLayout.spec(index / 5)
                setMargins(spacing, spacing, spacing, spacing)
            }
            grid.addView(cell, params)
        }
    }

    private fun scrollToLetter(letter: String) {
        val position = fullItemsList.indexOfFirst {
            it is ContactListItem.Header && it.letter == letter
        }
        if (position >= 0) {
            (binding.contactsRecyclerView.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(position, 0)
        }
    }
}
