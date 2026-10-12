package com.cubeslauncher.contacts

import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.cubeslauncher.contacts.R
import com.cubeslauncher.contacts.model.EmailEntry
import com.cubeslauncher.contacts.model.PhoneEntry
import com.cubeslauncher.contacts.databinding.ActivityContactEditBinding
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper

class ContactEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONTACT_ID = "extra_contact_id"
    }

    private lateinit var binding: ActivityContactEditBinding
    private lateinit var repository: ContactsRepository
    private var editingContactId: Long = -1
    private var isEditMode = false

    private val phoneRows = mutableListOf<View>()
    private val emailRows = mutableListOf<View>()

    private val phoneTypes = listOf(
        "Mobile" to CommonDataKinds.Phone.TYPE_MOBILE,
        "Home" to CommonDataKinds.Phone.TYPE_HOME,
        "Work" to CommonDataKinds.Phone.TYPE_WORK,
        "Other" to CommonDataKinds.Phone.TYPE_OTHER
    )

    private val emailTypes = listOf(
        "Home" to CommonDataKinds.Email.TYPE_HOME,
        "Work" to CommonDataKinds.Email.TYPE_WORK,
        "Other" to CommonDataKinds.Email.TYPE_OTHER
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityContactEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ContactsRepository(contentResolver)
        editingContactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1)
        isEditMode = editingContactId != -1L

        applyWindowInsets()
        setupAccountPicker()
        setupButtons()

        if (isEditMode) {
            binding.editTitle.text = getString(R.string.contacts_edit)
            loadExistingContact()
        } else {
            binding.editTitle.text = getString(R.string.contacts_add)
            addPhoneRow(null)
            addEmailRow(null)
        }

        binding.editTitle.setTextColor(AccentColorManager.getAccentColor())
        binding.saveButton.setBackgroundColor(AccentColorManager.getAccentColor())
        binding.addPhoneButton.setTextColor(AccentColorManager.getAccentColor())
        binding.addEmailButton.setTextColor(AccentColorManager.getAccentColor())
    }

    @Deprecated("Use onBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentContainer) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val density = resources.displayMetrics.density
            view.setPadding(
                view.paddingLeft,
                systemBars.top + (16 * density).toInt(),
                view.paddingRight,
                systemBars.bottom + (24 * density).toInt()
            )
            insets
        }
    }

    private fun setupAccountPicker() {
        val accounts = repository.getAccounts()
        val labels = accounts.map { (name, _) ->
            name ?: getString(R.string.contacts_device)
        }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.accountSpinner.adapter = adapter
    }

    private fun setupButtons() {
        binding.addPhoneButton.setOnClickListener { addPhoneRow(null) }
        binding.addEmailButton.setOnClickListener { addEmailRow(null) }

        binding.saveButton.setOnClickListener { saveContact() }
        binding.cancelButton.setOnClickListener { finish() }
    }

    private fun loadExistingContact() {
        val contact = repository.readContact(editingContactId) ?: run {
            finish()
            return
        }

        val nameParts = contact.displayName.split(" ", limit = 2)
        binding.firstNameInput.setText(nameParts.getOrElse(0) { "" })
        binding.lastNameInput.setText(nameParts.getOrElse(1) { "" })

        if (contact.phones.isEmpty()) {
            addPhoneRow(null)
        } else {
            for (phone in contact.phones) {
                addPhoneRow(phone)
            }
        }

        if (contact.emails.isEmpty()) {
            addEmailRow(null)
        } else {
            for (email in contact.emails) {
                addEmailRow(email)
            }
        }

        // Select the correct account
        val accounts = repository.getAccounts()
        val accountIndex = accounts.indexOfFirst { it.first == contact.accountName }
        if (accountIndex >= 0) {
            binding.accountSpinner.setSelection(accountIndex)
        }
    }

    private fun addPhoneRow(phone: PhoneEntry?) {
        val inflater = LayoutInflater.from(this)
        val row = inflater.inflate(R.layout.item_contact_edit_phone, binding.phonesContainer, false)

        val input = row.findViewById<EditText>(R.id.phoneInput)
        val typeSpinner = row.findViewById<Spinner>(R.id.phoneTypeSpinner)
        val removeBtn = row.findViewById<TextView>(R.id.removeButton)

        val typeLabels = phoneTypes.map { it.first }
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeLabels)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter

        if (phone != null) {
            input.setText(phone.number)
            val typeIndex = phoneTypes.indexOfFirst { it.second == phone.type }
            if (typeIndex >= 0) typeSpinner.setSelection(typeIndex)
        }

        removeBtn.setOnClickListener {
            binding.phonesContainer.removeView(row)
            phoneRows.remove(row)
        }

        phoneRows.add(row)
        binding.phonesContainer.addView(row)
    }

    private fun addEmailRow(email: EmailEntry?) {
        val inflater = LayoutInflater.from(this)
        val row = inflater.inflate(R.layout.item_contact_edit_email, binding.emailsContainer, false)

        val input = row.findViewById<EditText>(R.id.emailInput)
        val typeSpinner = row.findViewById<Spinner>(R.id.emailTypeSpinner)
        val removeBtn = row.findViewById<TextView>(R.id.removeButton)

        val typeLabels = emailTypes.map { it.first }
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, typeLabels)
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter

        if (email != null) {
            input.setText(email.address)
            val typeIndex = emailTypes.indexOfFirst { it.second == email.type }
            if (typeIndex >= 0) typeSpinner.setSelection(typeIndex)
        }

        removeBtn.setOnClickListener {
            binding.emailsContainer.removeView(row)
            emailRows.remove(row)
        }

        emailRows.add(row)
        binding.emailsContainer.addView(row)
    }

    private fun saveContact() {
        val firstName = binding.firstNameInput.text.toString().trim()
        val lastName = binding.lastNameInput.text.toString().trim()

        if (firstName.isEmpty() && lastName.isEmpty()) {
            binding.firstNameInput.requestFocus()
            return
        }

        val phones = phoneRows.mapNotNull { row ->
            val number = row.findViewById<EditText>(R.id.phoneInput).text.toString().trim()
            val typeSpinner = row.findViewById<Spinner>(R.id.phoneTypeSpinner)
            if (number.isNotEmpty()) {
                PhoneEntry(number, phoneTypes[typeSpinner.selectedItemPosition].second, null)
            } else null
        }

        val emails = emailRows.mapNotNull { row ->
            val address = row.findViewById<EditText>(R.id.emailInput).text.toString().trim()
            val typeSpinner = row.findViewById<Spinner>(R.id.emailTypeSpinner)
            if (address.isNotEmpty()) {
                EmailEntry(address, emailTypes[typeSpinner.selectedItemPosition].second, null)
            } else null
        }

        val success = if (isEditMode) {
            repository.updateContact(editingContactId, firstName, lastName, phones, emails)
        } else {
            val accounts = repository.getAccounts()
            val selectedAccount = accounts[binding.accountSpinner.selectedItemPosition]
            repository.createContact(firstName, lastName, phones, emails, selectedAccount.first, selectedAccount.second)
        }

        if (success) {
            Toast.makeText(this, R.string.contacts_saved, Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
