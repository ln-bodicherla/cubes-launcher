package com.cubeslauncher.messages.whatsapp

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper
import com.cubeslauncher.messages.MessagesPreferencesManager
import com.cubeslauncher.messages.databinding.ActivityWhatsappGroupInfoBinding
import com.cubeslauncher.messages.databinding.ItemGroupParticipantBinding
import com.cubeslauncher.messages.whatsapp.model.GroupParticipant
import com.cubeslauncher.messages.whatsapp.model.WhatsAppGroupInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WhatsAppGroupInfoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_JID = "extra_jid"
    }

    private lateinit var binding: ActivityWhatsappGroupInfoBinding
    private var currentThemeMode: String = "dark"
    private var jid: String = ""
    private var groupInfo: WhatsAppGroupInfo? = null
    private var isCurrentUserAdmin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        currentThemeMode = runBlocking { MessagesPreferencesManager(applicationContext).themeMode.first() }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityWhatsappGroupInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        jid = intent.getStringExtra(EXTRA_JID) ?: ""

        setupHeader()
        setupActions()
        applyWindowInsets()
        loadGroupInfo()
    }

    override fun onResume() {
        super.onResume()
        val newMode = runBlocking { MessagesPreferencesManager(applicationContext).themeMode.first() }
        if (newMode != currentThemeMode) {
            recreate()
        }
    }

    private fun setupHeader() {
        binding.backButton.setOnClickListener { finish() }
    }

    private fun setupActions() {
        binding.inviteLinkButton.setOnClickListener {
            lifecycleScope.launch {
                val link = withContext(Dispatchers.IO) {
                    WhatsAppBridge.getGroupInviteLink(jid)
                }
                if (link.isNotEmpty()) {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Invite Link", link))
                    Toast.makeText(this@WhatsAppGroupInfoActivity, "Invite link copied", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@WhatsAppGroupInfoActivity, "Failed to get invite link", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.leaveGroupButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Leave group")
                .setMessage("Are you sure you want to leave this group?")
                .setPositiveButton("Leave") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            WhatsAppBridge.leaveGroup(jid)
                        }
                        Toast.makeText(this@WhatsAppGroupInfoActivity, "Left group", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.header) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(view.paddingLeft, topInset + 16, view.paddingRight, view.paddingBottom)
            insets
        }
    }

    private fun loadGroupInfo() {
        lifecycleScope.launch {
            val json = withContext(Dispatchers.IO) {
                WhatsAppBridge.getGroupInfo(jid)
            }
            val info = parseGroupInfo(json)
            if (info != null) {
                groupInfo = info
                displayGroupInfo(info)
            }
        }
    }

    private fun parseGroupInfo(json: String): WhatsAppGroupInfo? {
        return try {
            val obj = JSONObject(json)
            val participantsArray = obj.optJSONArray("participants")
            val participants = mutableListOf<GroupParticipant>()
            if (participantsArray != null) {
                for (i in 0 until participantsArray.length()) {
                    val p = participantsArray.getJSONObject(i)
                    participants.add(
                        GroupParticipant(
                            jid = p.getString("jid"),
                            name = p.optString("name", ""),
                            isAdmin = p.optBoolean("isAdmin", false),
                            isSuperAdmin = p.optBoolean("isSuperAdmin", false)
                        )
                    )
                }
            }
            WhatsAppGroupInfo(
                jid = obj.optString("jid", ""),
                name = obj.optString("name", ""),
                topic = obj.optString("topic", ""),
                participants = participants,
                createdAt = obj.optLong("createdAt", 0),
                isLocked = obj.optBoolean("isLocked", false),
                isAnnounce = obj.optBoolean("isAnnounce", false)
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun displayGroupInfo(info: WhatsAppGroupInfo) {
        binding.groupName.setText(info.name)
        binding.groupTopic.setText(info.topic)
        binding.memberCount.text = "${info.participants.size} participants"

        // Determine if the current user is an admin
        isCurrentUserAdmin = info.participants.any {
            (it.isAdmin || it.isSuperAdmin) && it.jid.contains("@s.whatsapp.net")
                    && WhatsAppBridge.isLoggedIn()
        }

        // Disable editing if not admin
        if (!isCurrentUserAdmin) {
            binding.groupName.isEnabled = false
            binding.groupTopic.isEnabled = false
        } else {
            // Save name changes on focus loss
            binding.groupName.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val newName = binding.groupName.text.toString().trim()
                    if (newName.isNotEmpty() && newName != info.name) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            WhatsAppBridge.setGroupName(jid, newName)
                        }
                    }
                }
            }

            // Save topic changes on focus loss
            binding.groupTopic.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val newTopic = binding.groupTopic.text.toString().trim()
                    if (newTopic != info.topic) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            WhatsAppBridge.setGroupTopic(jid, newTopic)
                        }
                    }
                }
            }
        }

        // Setup participants RecyclerView
        binding.participantsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.participantsRecyclerView.adapter = GroupParticipantAdapter(info.participants)

        // Apply accent color to invite link button
        val accentColor = AccentColorManager.getAccentColor()
        binding.inviteLinkButton.setTextColor(accentColor)
    }

    // --- Participant Adapter ---

    private inner class GroupParticipantAdapter(
        private val participants: List<GroupParticipant>
    ) : RecyclerView.Adapter<GroupParticipantAdapter.ViewHolder>() {

        inner class ViewHolder(val binding: ItemGroupParticipantBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val itemBinding = ItemGroupParticipantBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(itemBinding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val participant = participants[position]
            val displayName = participant.name.ifEmpty {
                "+" + participant.jid.substringBefore("@")
            }

            holder.binding.participantName.text = displayName

            // Set initials
            val initials = displayName
                .split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercase() }
                .joinToString("")
                .ifEmpty { "?" }
            holder.binding.participantInitials.text = initials

            // Apply accent color to initials background
            val accentColor = AccentColorManager.getAccentColor()
            holder.binding.participantInitials.background.setTint(accentColor)

            // Show admin badge
            when {
                participant.isSuperAdmin -> {
                    holder.binding.participantRole.text = "Group creator"
                    holder.binding.participantRole.visibility = View.VISIBLE
                }
                participant.isAdmin -> {
                    holder.binding.participantRole.text = "Admin"
                    holder.binding.participantRole.visibility = View.VISIBLE
                }
                else -> {
                    holder.binding.participantRole.visibility = View.GONE
                }
            }
        }

        override fun getItemCount(): Int = participants.size
    }
}
