package com.cubeslauncher.clock

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.cubeslauncher.clock.databinding.ActivityClockBinding
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ClockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityClockBinding
    private var currentThemeMode: String = "dark"
    private var currentAccentColor: String = "#0078D7"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        val prefs = ClockPreferencesManager(applicationContext)
        currentThemeMode = runBlocking { prefs.themeMode.first() }
        currentAccentColor = runBlocking { prefs.accentColor.first() }
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityClockBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AccentColorManager.init(applicationContext)

        setupViewPager()
        setupTabs()
        setupSettings()
        applyAccentColor()
        applyWindowInsets()

        if (savedInstanceState == null) {
            binding.viewPager.setCurrentItem(0, false) // Alarm default
            updatePivotSelection(0)
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = ClockPreferencesManager(applicationContext)
        val newMode = runBlocking { prefs.themeMode.first() }
        val newAccent = runBlocking { prefs.accentColor.first() }
        if (newMode != currentThemeMode || newAccent != currentAccentColor) {
            recreate()
        }
    }

    @Deprecated("Use onBackPressedDispatcher", ReplaceWith("onBackPressedDispatcher"))
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = ClockPagerAdapter(this)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePivotSelection(position)
            }
        })
    }

    private fun setupTabs() {
        binding.tabAlarm.setOnClickListener { binding.viewPager.currentItem = 0 }
        binding.tabWorldClock.setOnClickListener { binding.viewPager.currentItem = 1 }
        binding.tabStopwatch.setOnClickListener { binding.viewPager.currentItem = 2 }
        binding.tabTimer.setOnClickListener { binding.viewPager.currentItem = 3 }
    }

    private fun setupSettings() {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, ClockSettingsActivity::class.java))
        }
    }

    private fun updatePivotSelection(selectedPosition: Int) {
        val activeColor = getThemedColor(R.attr.wpTextPrimary)
        val inactiveColor = getThemedColor(R.attr.wpTextSecondary)

        val tabs = listOf(
            binding.tabAlarm,
            binding.tabWorldClock,
            binding.tabStopwatch,
            binding.tabTimer
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
                    // This is intentionally a no-op for the pivot, matching WP10 behavior
                }
            }
        }
    }

    private fun getThemedColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }
}
