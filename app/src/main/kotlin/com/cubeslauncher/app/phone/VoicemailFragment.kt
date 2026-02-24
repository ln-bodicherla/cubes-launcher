package com.cubeslauncher.app.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.FragmentPhoneVoicemailBinding
import com.cubeslauncher.app.util.AccentColorManager

class VoicemailFragment : Fragment() {

    private var _binding: FragmentPhoneVoicemailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhoneVoicemailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val accent = AccentColorManager.getAccentColor()
        binding.voicemailIcon.setColorFilter(accent)

        setupVoicemail()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupVoicemail() {
        val activity = requireActivity()
        val hasPhoneState = activity is PhoneActivity &&
            activity.hasPermission(Manifest.permission.READ_PHONE_STATE)
        val hasCallPhone = activity is PhoneActivity &&
            activity.hasPermission(Manifest.permission.CALL_PHONE)

        if (!hasPhoneState) {
            showStatus(getString(R.string.phone_voicemail_permission_needed))
            return
        }

        try {
            val telephony = requireContext().getSystemService(TelephonyManager::class.java)
            val vmNumber = telephony?.voiceMailNumber

            if (vmNumber.isNullOrEmpty()) {
                showStatus(getString(R.string.phone_voicemail_not_configured))
                return
            }

            // Show voicemail number
            binding.voicemailNumber.text = getString(R.string.phone_voicemail_number, vmNumber)
            binding.voicemailNumber.visibility = View.VISIBLE

            // Show call button
            binding.callVoicemailButton.visibility = View.VISIBLE
            binding.callVoicemailButton.setOnClickListener {
                callVoicemail(vmNumber, hasCallPhone)
            }
        } catch (_: SecurityException) {
            showStatus(getString(R.string.phone_voicemail_permission_needed))
        }
    }

    private fun showStatus(message: String) {
        binding.voicemailStatus.text = message
        binding.voicemailStatus.visibility = View.VISIBLE
        binding.voicemailNumber.visibility = View.GONE
        binding.callVoicemailButton.visibility = View.GONE
    }

    private fun callVoicemail(vmNumber: String, hasCallPhone: Boolean) {
        if (!hasCallPhone) {
            showStatus(getString(R.string.phone_permission_needed))
            return
        }

        val telecom = requireContext().getSystemService(TelecomManager::class.java)
        val uri = Uri.fromParts("tel", vmNumber, null)
        try {
            telecom?.placeCall(uri, Bundle())
        } catch (_: SecurityException) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$vmNumber"))
                startActivity(intent)
            } catch (_: Exception) {
                showStatus(getString(R.string.phone_permission_needed))
            }
        }
    }
}
