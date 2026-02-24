package com.cubeslauncher.app.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.ActivityMessagesBinding
import com.cubeslauncher.app.util.PreferencesManager
import com.cubeslauncher.app.util.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MessagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMessagesBinding
    private var currentThemeMode: String = "dark"

    private val smsPermissions = arrayOf(
        Manifest.permission.SEND_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.RECEIVE_MMS,
        Manifest.permission.RECEIVE_WAP_PUSH,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_CONTACTS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            refreshCurrentTab()
        }
    }

    private val smsRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        currentThemeMode = runBlocking { PreferencesManager(applicationContext).themeMode.first() }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMessagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupTabs()
        setupSettings()
        applyWindowInsets()
        requestPermissionsIfNeeded()

        handleSendIntent(intent)

        if (savedInstanceState == null) {
            binding.viewPager.setCurrentItem(0, false) // Messages default
            updatePivotSelection(0)
        }

        requestDefaultSmsRole()
    }

    override fun onResume() {
        super.onResume()
        val newMode = runBlocking { PreferencesManager(applicationContext).themeMode.first() }
        if (newMode != currentThemeMode) {
            recreate()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSendIntent(intent)
    }

    @Deprecated("Use onBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun handleSendIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SENDTO || intent?.action == Intent.ACTION_SEND) {
            val uri = intent.data
            if (uri != null) {
                val scheme = uri.scheme
                if (scheme == "sms" || scheme == "smsto" || scheme == "mms" || scheme == "mmsto") {
                    val address = uri.schemeSpecificPart?.replace("-", "")?.replace(" ", "") ?: return
                    if (address.isNotEmpty()) {
                        val conversationIntent = Intent(this, ConversationActivity::class.java).apply {
                            putExtra(ConversationActivity.EXTRA_ADDRESS, address)
                        }
                        startActivity(conversationIntent)
                    }
                }
            }
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = MessagesPagerAdapter(this)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePivotSelection(position)
            }
        })
    }

    private fun setupTabs() {
        binding.tabMessages.setOnClickListener { binding.viewPager.currentItem = 0 }
        binding.tabGroups.setOnClickListener { binding.viewPager.currentItem = 1 }
        binding.tabArchived.setOnClickListener { binding.viewPager.currentItem = 2 }
        binding.tabDeleted.setOnClickListener { binding.viewPager.currentItem = 3 }
    }

    private fun setupSettings() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, MessagesSettingsActivity::class.java))
        }
    }

    private fun updatePivotSelection(selectedPosition: Int) {
        val activeColor = getThemedColor(R.attr.wpTextPrimary)
        val inactiveColor = getThemedColor(R.attr.wpTextSecondary)

        val tabs = listOf(
            binding.tabMessages,
            binding.tabGroups,
            binding.tabArchived,
            binding.tabDeleted
        )

        for ((index, tab) in tabs.withIndex()) {
            if (index == selectedPosition) {
                tab.setTextColor(activeColor)
            } else {
                tab.setTextColor(inactiveColor)
            }
            tab.typeface = resources.getFont(R.font.selawik_semilight)
        }

        // Auto-scroll so the selected tab is centered in the HorizontalScrollView
        val selectedTab = tabs[selectedPosition]
        selectedTab.post {
            val scrollView = binding.pivotScrollView
            val scrollTo = selectedTab.left + selectedTab.width / 2 - scrollView.width / 2
            scrollView.smoothScrollTo(scrollTo, 0)
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.viewPager) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, view.paddingBottom)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.pivotContainer) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)
            insets
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = smsPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun refreshCurrentTab() {
        val currentItem = binding.viewPager.currentItem
        binding.viewPager.adapter = MessagesPagerAdapter(this)
        binding.viewPager.setCurrentItem(currentItem, false)
    }

    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java) ?: return
            if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                smsRoleLauncher.launch(intent)
            }
        }
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun getThemedColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }

    private inner class MessagesPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> MessagesListFragment()
            1 -> MessagesGroupsFragment()
            2 -> ArchivedFragment()
            3 -> DeletedFragment()
            else -> MessagesListFragment()
        }
    }
}
