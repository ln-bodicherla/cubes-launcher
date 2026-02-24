package com.cubeslauncher.app.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.cubeslauncher.app.R
import com.cubeslauncher.app.contacts.model.Contact
import com.cubeslauncher.app.databinding.ActivityContactDetailBinding
import android.graphics.drawable.GradientDrawable
import com.cubeslauncher.app.util.AccentColorManager
import com.cubeslauncher.app.util.ThemeHelper

class ContactDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CONTACT_ID = "extra_contact_id"
    }

    private lateinit var binding: ActivityContactDetailBinding
    private lateinit var repository: ContactsRepository
    private var contactId: Long = -1
    private var contact: Contact? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityContactDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = ContactsRepository(contentResolver)
        contactId = intent.getLongExtra(EXTRA_CONTACT_ID, -1)

        applyWindowInsets()
        loadContact()
    }

    override fun onResume() {
        super.onResume()
        loadContact()
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

            binding.contentContainer.setPadding(
                binding.contentContainer.paddingLeft,
                binding.contentContainer.paddingTop,
                binding.contentContainer.paddingRight,
                systemBars.bottom + (24 * density).toInt()
            )
            insets
        }
    }

    private fun loadContact() {
        if (contactId == -1L) {
            finish()
            return
        }

        contact = repository.readContact(contactId)
        if (contact == null) {
            finish()
            return
        }

        val c = contact!!
        // Split name into two lines (first name / last name)
        val nameParts = c.displayName.trim().split("\\s+".toRegex(), limit = 2)
        binding.contactName.text = nameParts.joinToString("\n")
        binding.contactName.setTextColor(getThemedColor(R.attr.wpTextPrimary))
        binding.contactName.typeface = resources.getFont(R.font.selawik_semibold)
        binding.contactInitials.text = getInitials(c.displayName)
        (binding.contactInitials.background as? GradientDrawable)?.setColor(AccentColorManager.getAccentColor())

        setupActionButtons(c)
        setupPhones(c)
        setupEmails(c)
        setupAccountInfo(c)
        setupBottomActions(c)
    }

    private fun setupActionButtons(contact: Contact) {
        val primaryPhone = contact.phones.firstOrNull()?.number
        val primaryEmail = contact.emails.firstOrNull()?.address

        binding.actionCall.setOnClickListener {
            if (primaryPhone != null) placeCall(primaryPhone)
        }

        binding.actionMessage.setOnClickListener {
            if (primaryPhone != null) {
                val smsUri = Uri.parse("smsto:$primaryPhone")
                try { startActivity(Intent(Intent.ACTION_SENDTO, smsUri)) } catch (_: Exception) { }
            }
        }

        binding.actionEmail.setOnClickListener {
            if (primaryEmail != null) {
                val emailUri = Uri.parse("mailto:$primaryEmail")
                try { startActivity(Intent(Intent.ACTION_SENDTO, emailUri)) } catch (_: Exception) { }
            }
        }
    }

    private fun setupPhones(contact: Contact) {
        binding.phonesContainer.removeAllViews()
        if (contact.phones.isEmpty()) {
            binding.phonesLabel.visibility = View.GONE
            return
        }
        binding.phonesLabel.visibility = View.VISIBLE

        for (phone in contact.phones) {
            val row = createInfoRow(
                getPhoneTypeLabel(phone.type),
                phone.number
            )
            row.setOnClickListener { placeCall(phone.number) }
            binding.phonesContainer.addView(row)
        }
    }

    private fun setupEmails(contact: Contact) {
        binding.emailsContainer.removeAllViews()
        if (contact.emails.isEmpty()) {
            binding.emailsLabel.visibility = View.GONE
            return
        }
        binding.emailsLabel.visibility = View.VISIBLE

        for (email in contact.emails) {
            val row = createInfoRow(
                getEmailTypeLabel(email.type),
                email.address
            )
            row.setOnClickListener {
                val uri = Uri.parse("mailto:${email.address}")
                try { startActivity(Intent(Intent.ACTION_SENDTO, uri)) } catch (_: Exception) { }
            }
            binding.emailsContainer.addView(row)
        }
    }

    private fun createInfoRow(label: String, value: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp, 0, 8.dp)
            isClickable = true
            isFocusable = true
        }
        val labelView = TextView(this).apply {
            text = label
            setTextColor(getThemedColor(R.attr.wpTextSecondary))
            textSize = 14f
            typeface = resources.getFont(R.font.selawik_regular)
        }
        val valueView = TextView(this).apply {
            text = value
            setTextColor(getThemedColor(R.attr.wpTextPrimary))
            textSize = 18f
            typeface = resources.getFont(R.font.selawik_regular)
        }
        row.addView(labelView)
        row.addView(valueView)
        return row
    }

    private fun setupAccountInfo(contact: Contact) {
        val accountName = contact.accountName
        if (accountName != null) {
            binding.accountInfo.text = getString(R.string.contacts_account) + ": " + accountName
            binding.accountInfo.visibility = View.VISIBLE
        } else {
            binding.accountInfo.text = getString(R.string.contacts_account) + ": " + getString(R.string.contacts_device)
            binding.accountInfo.visibility = View.VISIBLE
        }
    }

    private fun setupBottomActions(contact: Contact) {
        updateStarUI(contact.starred)

        binding.actionStar.setOnClickListener {
            val newStarred = !(this.contact?.starred ?: false)
            if (repository.toggleStar(contactId, newStarred)) {
                this.contact = this.contact?.copy(starred = newStarred)
                updateStarUI(newStarred)
            }
        }

        binding.actionEdit.setOnClickListener {
            val intent = Intent(this, ContactEditActivity::class.java).apply {
                putExtra(ContactEditActivity.EXTRA_CONTACT_ID, contactId)
            }
            startActivity(intent)
        }

        binding.actionDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage(R.string.contacts_delete_confirm)
                .setPositiveButton(R.string.contacts_delete_yes) { _, _ ->
                    if (repository.deleteContact(contactId)) {
                        Toast.makeText(this, R.string.contacts_deleted, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .setNegativeButton(R.string.contacts_delete_no, null)
                .show()
        }
    }

    private fun updateStarUI(starred: Boolean) {
        if (starred) {
            binding.starIcon.setImageResource(R.drawable.ic_contacts_star)
            binding.starLabel.text = "unstar"
        } else {
            binding.starIcon.setImageResource(R.drawable.ic_contacts_star_outline)
            binding.starLabel.text = "star"
        }
    }

    private fun placeCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.contacts_permission_needed, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.fromParts("tel", number, null)
        val telecom = getSystemService(TelecomManager::class.java)
        try {
            telecom?.placeCall(uri, Bundle())
        } catch (_: SecurityException) {
            try {
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
            } catch (_: Exception) {
                Toast.makeText(this, R.string.contacts_permission_needed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getPhoneTypeLabel(type: Int): String {
        return when (type) {
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Home"
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Mobile"
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Work"
            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Other"
            else -> "Phone"
        }
    }

    private fun getEmailTypeLabel(type: Int): String {
        return when (type) {
            android.provider.ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "Home"
            android.provider.ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "Work"
            android.provider.ContactsContract.CommonDataKinds.Email.TYPE_OTHER -> "Other"
            else -> "Email"
        }
    }

    private fun getInitials(name: String): String {
        val parts = name.trim().split("\\s+".toRegex())
        return when {
            parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}".uppercase()
            parts.isNotEmpty() && parts[0].isNotEmpty() -> parts[0].first().uppercase()
            else -> "?"
        }
    }

    private fun getThemedColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0xFFFFFFFF.toInt())
        ta.recycle()
        return color
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
