package com.cubeslauncher.app.phone

import android.Manifest
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.app.databinding.FragmentPhoneSpeedDialBinding
import com.cubeslauncher.app.phone.model.SpeedDialContact

class SpeedDialFragment : Fragment() {

    private var _binding: FragmentPhoneSpeedDialBinding? = null
    private val binding get() = _binding!!
    private var adapter: SpeedDialAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhoneSpeedDialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.speedDialRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadStarredContacts()
    }

    override fun onResume() {
        super.onResume()
        loadStarredContacts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapter = null
        _binding = null
    }

    private fun loadStarredContacts() {
        val activity = requireActivity()
        if (activity is PhoneActivity && !activity.hasPermission(Manifest.permission.READ_CONTACTS)) {
            showEmpty()
            return
        }

        val contacts = queryStarredContacts()
        if (contacts.isEmpty()) {
            showEmpty()
            return
        }

        if (adapter == null) {
            adapter = SpeedDialAdapter(contacts) { contact ->
                val phoneActivity = requireActivity() as? PhoneActivity
                phoneActivity?.navigateToKeypadWithNumber(contact.phoneNumber)
            }
            binding.speedDialRecyclerView.adapter = adapter
        } else {
            adapter!!.updateContacts(contacts)
        }
        binding.emptyState.visibility = View.GONE
        binding.speedDialRecyclerView.visibility = View.VISIBLE
    }

    private fun queryStarredContacts(): List<SpeedDialContact> {
        val contacts = mutableListOf<SpeedDialContact>()
        var cursor: Cursor? = null

        try {
            cursor = requireContext().contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                ),
                "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1",
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            val seenIds = mutableSetOf<Long>()
            cursor?.let {
                val idIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                while (it.moveToNext()) {
                    val contactId = it.getLong(idIdx)
                    if (seenIds.contains(contactId)) continue
                    seenIds.add(contactId)

                    val photoStr = it.getString(photoIdx)
                    contacts.add(
                        SpeedDialContact(
                            contactId = contactId,
                            name = it.getString(nameIdx) ?: "",
                            phoneNumber = it.getString(numberIdx) ?: "",
                            photoUri = if (photoStr != null) Uri.parse(photoStr) else null
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted
        } finally {
            cursor?.close()
        }

        return contacts
    }

    private fun showEmpty() {
        binding.emptyState.visibility = View.VISIBLE
        binding.speedDialRecyclerView.visibility = View.GONE
    }
}
