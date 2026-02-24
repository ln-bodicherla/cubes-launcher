package com.cubeslauncher.app.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
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
import com.cubeslauncher.app.databinding.ActivityContactsBinding
import com.cubeslauncher.app.util.AccentColorManager
import com.cubeslauncher.app.util.PreferencesManager
import com.cubeslauncher.app.util.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private var currentThemeMode: String = "dark"

    private val contactsPermissions = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS
    )

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            refreshCurrentTab()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        currentThemeMode = runBlocking { PreferencesManager(applicationContext).themeMode.first() }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupTabs()
        setupSettings()
        applyAccentColor()
        applyWindowInsets()
        requestPermissionsIfNeeded()

        if (savedInstanceState == null) {
            binding.viewPager.setCurrentItem(0, false)
            updatePivotSelection(0)
        }
    }

    override fun onResume() {
        super.onResume()
        val newMode = runBlocking { PreferencesManager(applicationContext).themeMode.first() }
        if (newMode != currentThemeMode) {
            recreate()
        }
    }

    @Deprecated("Use onBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = ContactsPagerAdapter(this)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePivotSelection(position)
            }
        })
    }

    private fun setupTabs() {
        binding.tabContacts.setOnClickListener { binding.viewPager.currentItem = 0 }
        binding.tabFavorites.setOnClickListener { binding.viewPager.currentItem = 1 }
        binding.tabGroups.setOnClickListener { binding.viewPager.currentItem = 2 }
    }

    private fun setupSettings() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, PeopleSettingsActivity::class.java))
        }
    }

    private fun updatePivotSelection(selectedPosition: Int) {
        val activeColor = getThemedColor(R.attr.wpTextPrimary)
        val inactiveColor = getThemedColor(R.attr.wpTextSecondary)

        val tabs = listOf(
            binding.tabContacts,
            binding.tabFavorites,
            binding.tabGroups
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

    private fun applyAccentColor() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AccentColorManager.accentColorFlow.collect {
                    // Pivot tabs don't use accent color — they use primary/secondary text
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val needed = contactsPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun refreshCurrentTab() {
        val currentItem = binding.viewPager.currentItem
        binding.viewPager.adapter = ContactsPagerAdapter(this)
        binding.viewPager.setCurrentItem(currentItem, false)
    }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun getThemedColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }

    private inner class ContactsPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> ContactsListFragment()
            1 -> FavoritesFragment()
            2 -> GroupsFragment()
            else -> ContactsListFragment()
        }
    }
}
