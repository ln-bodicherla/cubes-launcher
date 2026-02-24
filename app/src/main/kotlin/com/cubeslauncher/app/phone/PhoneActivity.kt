package com.cubeslauncher.app.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.graphics.Color
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.ActivityPhoneBinding
import com.cubeslauncher.app.util.AccentColorManager
import com.cubeslauncher.app.util.PreferencesManager
import com.cubeslauncher.app.util.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PhoneActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhoneBinding
    private var currentThemeMode: String = "dark"

    private val phonePermissions = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            refreshCurrentTab()
        }
    }

    private val dialerRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        currentThemeMode = runBlocking { PreferencesManager(applicationContext).themeMode.first() }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityPhoneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupTabs()
        setupSettings()
        applyAccentColor()
        applyWindowInsets()
        requestPermissionsIfNeeded()

        handleDialIntent(intent)

        if (savedInstanceState == null) {
            binding.viewPager.setCurrentItem(1, false) // History default
            updatePivotSelection(1)
        }

        requestDefaultDialerRole()
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
        handleDialIntent(intent)
    }

    @Deprecated("Use onBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun handleDialIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_DIAL || intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data
            if (uri?.scheme == "tel") {
                val number = uri.schemeSpecificPart
                if (!number.isNullOrEmpty()) {
                    binding.viewPager.setCurrentItem(2, false) // Keypad page
                    binding.viewPager.post {
                        val fragment = supportFragmentManager.findFragmentByTag("f2")
                        if (fragment is KeypadFragment) {
                            fragment.setNumber(number)
                        }
                    }
                }
            }
        }
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = PhonePagerAdapter(this)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePivotSelection(position)
            }
        })
    }

    private fun setupTabs() {
        binding.tabSpeedDial.setOnClickListener { binding.viewPager.currentItem = 0 }
        binding.tabHistory.setOnClickListener { binding.viewPager.currentItem = 1 }
        binding.tabKeypad.setOnClickListener { binding.viewPager.currentItem = 2 }
        binding.tabVoicemail.setOnClickListener { binding.viewPager.currentItem = 3 }
    }

    private fun setupSettings() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, PhoneSettingsActivity::class.java))
        }
    }

    private fun updatePivotSelection(selectedPosition: Int) {
        val activeColor = getThemedColor(R.attr.wpTextPrimary)
        val inactiveColor = getThemedColor(R.attr.wpTextSecondary)

        val tabs = listOf(
            binding.tabSpeedDial,
            binding.tabHistory,
            binding.tabKeypad,
            binding.tabVoicemail
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
        // Top inset on ViewPager2 — prevents content scrolling behind notch/status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.viewPager) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            view.setPadding(view.paddingLeft, topInset, view.paddingRight, view.paddingBottom)
            insets
        }

        // Bottom inset on pivot tab container
        ViewCompat.setOnApplyWindowInsetsListener(binding.pivotContainer) { view, insets ->
            val bottomInset = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, bottomInset)
            insets
        }
    }

    private fun applyAccentColor() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AccentColorManager.accentColorFlow.collect {
                    // Pivot tabs don't use accent color — they use primary/secondary text
                    // This is intentionally a no-op for the pivot, matching WP10 behavior
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = phonePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun refreshCurrentTab() {
        // ViewPager2 manages fragments — trigger reload by re-setting the adapter
        val currentItem = binding.viewPager.currentItem
        binding.viewPager.adapter = PhonePagerAdapter(this)
        binding.viewPager.setCurrentItem(currentItem, false)
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java) ?: return
            if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                dialerRoleLauncher.launch(intent)
            }
        } else {
            val telecom = getSystemService(TELECOM_SERVICE) as? TelecomManager ?: return
            if (telecom.defaultDialerPackage != packageName) {
                val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                    putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                }
                dialerRoleLauncher.launch(intent)
            }
        }
    }

    fun navigateToKeypadWithNumber(number: String) {
        binding.viewPager.setCurrentItem(2, true)
        binding.viewPager.post {
            val fragment = supportFragmentManager.findFragmentByTag("f2")
            if (fragment is KeypadFragment) {
                fragment.setNumber(number)
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

    private inner class PhonePagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> SpeedDialFragment()
            1 -> CallHistoryFragment()
            2 -> KeypadFragment()
            3 -> VoicemailFragment()
            else -> KeypadFragment()
        }
    }
}
