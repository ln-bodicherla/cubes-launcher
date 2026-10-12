package com.cubeslauncher.launcher

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.widget.ViewPager2
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cubeslauncher.launcher.appslist.AppsListFragment
import com.cubeslauncher.launcher.databinding.ActivityLauncherBinding
import com.cubeslauncher.launcher.home.HomeFragment
import com.cubeslauncher.launcher.receiver.PackageChangeReceiver
import com.cubeslauncher.common.theme.AccentColorManager
import com.cubeslauncher.launcher.util.LauncherPreferencesManager
import com.cubeslauncher.common.theme.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private val packageChangeReceiver = PackageChangeReceiver()
    private var currentThemeMode: String = "dark"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        currentThemeMode = runBlocking { LauncherPreferencesManager(applicationContext).themeMode.first() }
        AccentColorManager.init(applicationContext)

        // Edge-to-edge with black status bar (stock icons visible)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = LauncherPagerAdapter(this)
        binding.viewPager.offscreenPageLimit = 1

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 0) {
                    // Switched to home — clear search in app list
                    val appsFragment = supportFragmentManager.fragments
                        .filterIsInstance<AppsListFragment>().firstOrNull()
                    appsFragment?.clearSearch()
                }
            }
        })

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Exit wiggle mode first if active
                val homeFragment = supportFragmentManager.fragments
                    .filterIsInstance<HomeFragment>().firstOrNull()
                if (homeFragment?.handleBackPress() == true) return

                // Close search first if open
                val appsFragment = supportFragmentManager.fragments
                    .filterIsInstance<AppsListFragment>().firstOrNull()
                if (appsFragment?.isSearchOpen() == true) {
                    appsFragment.hideSearch()
                    return
                }

                // Close letter grid if open
                if (appsFragment?.isLetterGridOpen() == true) {
                    appsFragment.closeLetterGrid()
                } else if (binding.viewPager.currentItem != 0) {
                    binding.viewPager.currentItem = 0
                }
            }
        })

        packageChangeReceiver.onPackageChanged = {
            val fragment = supportFragmentManager.fragments
                .filterIsInstance<AppsListFragment>().firstOrNull()
            fragment?.loadApps()
        }

        observeAccentColor()
    }

    private fun observeAccentColor() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AccentColorManager.accentColorFlow.collect {
                    // Refresh apps list accent colors
                    val appsFragment = supportFragmentManager.fragments
                        .filterIsInstance<AppsListFragment>().firstOrNull()
                    appsFragment?.refreshAccentColors()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val newMode = runBlocking { LauncherPreferencesManager(applicationContext).themeMode.first() }
        if (newMode != currentThemeMode) {
            recreate()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageChangeReceiver, filter)
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(packageChangeReceiver)
    }
}
