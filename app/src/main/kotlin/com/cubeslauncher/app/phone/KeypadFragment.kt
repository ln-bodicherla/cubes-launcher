package com.cubeslauncher.app.phone

import android.Manifest
import android.content.Intent
import android.database.Cursor
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.FragmentPhoneKeypadBinding
import com.cubeslauncher.app.databinding.ItemPhoneT9ResultBinding

class KeypadFragment : Fragment() {

    private var _binding: FragmentPhoneKeypadBinding? = null
    private val binding get() = _binding!!

    private val numberBuilder = StringBuilder()
    private var pendingNumber: String? = null
    private var toneGenerator: ToneGenerator? = null

    private val t9Map = mapOf(
        '2' to "abc", '3' to "def",
        '4' to "ghi", '5' to "jkl", '6' to "mno",
        '7' to "pqrs", '8' to "tuv", '9' to "wxyz"
    )

    private var t9Adapter: T9Adapter? = null

    private var simHelper: SimSelectionHelper? = null
    private var availableSims = listOf<SimSelectionHelper.SimInfo>()
    private var selectedSimIndex = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhoneKeypadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_DTMF, 80)
        } catch (_: Exception) {
            null
        }

        setupT9Results()
        setupDialKeys()
        setupBottomButtons()
        setupBackspace()
        setupSimSelector()

        // Handle pending number from setNumber() called before view was created
        pendingNumber?.let {
            setNumber(it)
            pendingNumber = null
        } ?: run {
            // Fallback: check arguments
            arguments?.getString("number")?.let { setNumber(it) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        toneGenerator?.release()
        toneGenerator = null
        t9Adapter = null
        simHelper = null
        _binding = null
    }

    fun setNumber(number: String) {
        if (_binding == null) {
            pendingNumber = number
            return
        }
        numberBuilder.clear()
        numberBuilder.append(number)
        updateDisplay()
    }

    private fun setupT9Results() {
        t9Adapter = T9Adapter { contact ->
            numberBuilder.clear()
            numberBuilder.append(contact.number)
            updateDisplay()
        }
        binding.t9Results.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.t9Results.adapter = t9Adapter
    }

    private fun setupDialKeys() {
        val keys = mapOf(
            binding.key0 to "0",
            binding.key1 to "1",
            binding.key2 to "2",
            binding.key3 to "3",
            binding.key4 to "4",
            binding.key5 to "5",
            binding.key6 to "6",
            binding.key7 to "7",
            binding.key8 to "8",
            binding.key9 to "9",
            binding.keyStar to "*",
            binding.keyHash to "#"
        )

        val dtmfTones = mapOf(
            "0" to ToneGenerator.TONE_DTMF_0,
            "1" to ToneGenerator.TONE_DTMF_1,
            "2" to ToneGenerator.TONE_DTMF_2,
            "3" to ToneGenerator.TONE_DTMF_3,
            "4" to ToneGenerator.TONE_DTMF_4,
            "5" to ToneGenerator.TONE_DTMF_5,
            "6" to ToneGenerator.TONE_DTMF_6,
            "7" to ToneGenerator.TONE_DTMF_7,
            "8" to ToneGenerator.TONE_DTMF_8,
            "9" to ToneGenerator.TONE_DTMF_9,
            "*" to ToneGenerator.TONE_DTMF_S,
            "#" to ToneGenerator.TONE_DTMF_P
        )

        for ((keyView, digit) in keys) {
            keyView.setOnClickListener {
                dtmfTones[digit]?.let { tone ->
                    toneGenerator?.startTone(tone, 150)
                }
                appendDigit(digit)
            }
        }

        // Long-press 0 = "+"
        binding.key0.setOnLongClickListener {
            if (numberBuilder.isNotEmpty() && numberBuilder.last() == '0') {
                numberBuilder.deleteCharAt(numberBuilder.lastIndex)
            }
            appendDigit("+")
            true
        }

        // Long-press 1 = voicemail
        binding.key1.setOnLongClickListener {
            callVoicemail()
            true
        }
    }

    private fun setupBottomButtons() {
        // "call" button — flat boxy Metro style
        binding.callButton.setOnClickListener {
            val number = numberBuilder.toString()
            if (number.isNotEmpty()) {
                placeCall(number)
            }
        }

        // "save" button — save number to contacts
        binding.saveButton.setOnClickListener {
            val number = numberBuilder.toString()
            if (number.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_INSERT).apply {
                    type = android.provider.ContactsContract.Contacts.CONTENT_TYPE
                    putExtra(android.provider.ContactsContract.Intents.Insert.PHONE, number)
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) { }
            }
        }
    }

    private fun setupBackspace() {
        binding.backspaceButton.setOnClickListener {
            if (numberBuilder.isNotEmpty()) {
                numberBuilder.deleteCharAt(numberBuilder.lastIndex)
                updateDisplay()
            }
        }
        binding.backspaceButton.setOnLongClickListener {
            numberBuilder.clear()
            updateDisplay()
            true
        }
    }

    private fun appendDigit(digit: String) {
        numberBuilder.append(digit)
        updateDisplay()
    }

    private fun updateDisplay() {
        binding.numberDisplay.text = numberBuilder.toString()
        binding.backspaceButton.visibility =
            if (numberBuilder.isNotEmpty()) View.VISIBLE else View.GONE

        // T9 search
        searchT9Contacts()
    }

    private fun searchT9Contacts() {
        val digits = numberBuilder.toString()

        // Only search if we have digits and they contain T9-mappable chars (2-9)
        val t9Digits = digits.filter { it in '2'..'9' }
        if (t9Digits.isEmpty()) {
            binding.t9Results.visibility = View.GONE
            t9Adapter?.updateResults(emptyList())
            return
        }

        val activity = requireActivity()
        if (activity is PhoneActivity && !activity.hasPermission(Manifest.permission.READ_CONTACTS)) {
            binding.t9Results.visibility = View.GONE
            return
        }

        val results = queryT9Contacts(t9Digits)
        if (results.isNotEmpty()) {
            t9Adapter?.updateResults(results)
            binding.t9Results.visibility = View.VISIBLE
        } else {
            t9Adapter?.updateResults(emptyList())
            binding.t9Results.visibility = View.GONE
        }
    }

    private fun queryT9Contacts(digits: String): List<T9Contact> {
        val results = mutableListOf<T9Contact>()

        // Build regex pattern from T9 digits
        val patternBuilder = StringBuilder()
        for (d in digits) {
            val letters = t9Map[d] ?: continue
            patternBuilder.append("[${letters.uppercase()}${letters}]")
        }
        if (patternBuilder.isEmpty()) return results

        val regex = try {
            Regex(".*${patternBuilder}.*", RegexOption.IGNORE_CASE)
        } catch (_: Exception) {
            return results
        }

        var cursor: Cursor? = null
        try {
            cursor = requireContext().contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            val seenNames = mutableSetOf<String>()
            cursor?.let {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (it.moveToNext() && results.size < 10) {
                    val name = it.getString(nameIdx) ?: continue
                    val number = it.getString(numberIdx) ?: continue

                    // Match: check if name matches T9 pattern (starts-with on any word)
                    val nameWords = name.split("\\s+".toRegex())
                    val matches = nameWords.any { word ->
                        word.length >= digits.length && matchesT9(word, digits)
                    }

                    if (matches && seenNames.add(name.lowercase())) {
                        results.add(T9Contact(name, number))
                    }
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted
        } finally {
            cursor?.close()
        }

        return results
    }

    private fun matchesT9(word: String, digits: String): Boolean {
        for (i in digits.indices) {
            if (i >= word.length) return false
            val letter = word[i].lowercaseChar()
            val expectedLetters = t9Map[digits[i]] ?: return false
            if (letter !in expectedLetters) return false
        }
        return true
    }

    private fun setupSimSelector() {
        simHelper = SimSelectionHelper(requireContext())
        availableSims = simHelper?.getAvailableSims() ?: emptyList()

        if (availableSims.size > 1) {
            binding.simSelector.visibility = View.VISIBLE
            updateSimLabel()
            binding.simSelector.setOnClickListener {
                selectedSimIndex = (selectedSimIndex + 1) % availableSims.size
                updateSimLabel()
            }
        } else {
            binding.simSelector.visibility = View.GONE
        }
    }

    private fun updateSimLabel() {
        if (availableSims.isNotEmpty() && selectedSimIndex < availableSims.size) {
            binding.simSelector.text = availableSims[selectedSimIndex].label
        }
    }

    fun getSelectedPhoneAccountHandle(): PhoneAccountHandle? {
        if (availableSims.isEmpty() || selectedSimIndex >= availableSims.size) return null
        return availableSims[selectedSimIndex].phoneAccountHandle
    }

    fun placeCall(number: String) {
        val activity = requireActivity()
        if (activity is PhoneActivity && !activity.hasPermission(Manifest.permission.CALL_PHONE)) {
            Toast.makeText(requireContext(), R.string.phone_permission_needed, Toast.LENGTH_SHORT).show()
            return
        }

        val uri = Uri.fromParts("tel", number, null)
        val telecom = requireContext().getSystemService(TelecomManager::class.java)
        try {
            val extras = Bundle()
            val phoneAccount = getSelectedPhoneAccountHandle()
            if (phoneAccount != null) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccount)
            }
            telecom?.placeCall(uri, extras)
        } catch (_: SecurityException) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
                startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(requireContext(), R.string.phone_permission_needed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun callVoicemail() {
        try {
            val telephony = requireContext().getSystemService(android.telephony.TelephonyManager::class.java)
            val vmNumber = telephony?.voiceMailNumber
            if (!vmNumber.isNullOrEmpty()) {
                placeCall(vmNumber)
            }
        } catch (_: SecurityException) {
            // Permission not granted
        }
    }

    // T9 data class
    data class T9Contact(val name: String, val number: String)

    // T9 adapter
    private class T9Adapter(
        private val onContactClick: (T9Contact) -> Unit
    ) : RecyclerView.Adapter<T9Adapter.ViewHolder>() {

        private var results = listOf<T9Contact>()

        fun updateResults(newResults: List<T9Contact>) {
            results = newResults
            notifyDataSetChanged()
        }

        inner class ViewHolder(
            private val binding: ItemPhoneT9ResultBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(contact: T9Contact) {
                binding.t9Name.text = contact.name
                binding.t9Number.text = contact.number
                binding.root.setOnClickListener { onContactClick(contact) }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPhoneT9ResultBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(results[position])
        }

        override fun getItemCount(): Int = results.size
    }
}
