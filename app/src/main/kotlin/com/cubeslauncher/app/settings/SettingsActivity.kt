package com.cubeslauncher.app.settings

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Switch
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.cubeslauncher.app.R
import com.cubeslauncher.app.databinding.ActivitySettingsBinding
import com.cubeslauncher.app.util.AccentColorManager
import com.cubeslauncher.app.util.PreferencesManager
import com.cubeslauncher.app.util.ThemeHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferencesManager

    private var pendingImageTarget: String? = null // "bg" or "lock"
    private var isUpdatingModes = false

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> handleImagePicked(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        binding.backButton.setOnClickListener { finish() }

        styleWp10()
        setupBackgroundMode()
        setupTileTransparency()
        setupShowMoreTiles()
        setupTileMode()
        setupMinimalTiles()
        setupUsageTracking()
        setupTheme()
        setupAccentColors()
        setupLockScreen()
    }

    // ---- WP10 Styling ----

    private fun styleWp10() {
        val accent = AccentColorManager.getAccentColor()

        // Section headers in accent color
        binding.sectionStart.setTextColor(accent)
        binding.sectionColors.setTextColor(accent)
        binding.sectionLockScreen.setTextColor(accent)

        // SeekBar accent tint
        val accentTint = ColorStateList.valueOf(accent)
        binding.tileTransparencySeekBar.progressTintList = accentTint
        binding.tileTransparencySeekBar.thumbTintList = accentTint

        // WP10 rectangular toggle switches
        styleWp10Switch(binding.showMoreTilesSwitch, accent)
        styleWp10Switch(binding.tileModeSwitch, accent)
        styleWp10Switch(binding.minimalTilesSwitch, accent)
        styleWp10Switch(binding.usageTrackingSwitch, accent)
    }

    private fun styleWp10Switch(switch: Switch, accent: Int) {
        val density = resources.displayMetrics.density
        val thumbW = (16 * density).toInt()
        val thumbH = (16 * density).toInt()
        val trackW = (44 * density).toInt()
        val trackH = (22 * density).toInt()
        val strokeW = (2 * density).toInt()

        // Resolve off-state border color from theme
        val tv = TypedValue()
        theme.resolveAttribute(R.attr.wpTextSecondary, tv, true)
        val borderColor = tv.data

        // Square white thumb
        val thumb = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            setSize(thumbW, thumbH)
        }

        // Track on: accent filled rectangle
        val trackOn = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(accent)
            setSize(trackW, trackH)
        }

        // Track off: transparent with border
        val trackOff = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.TRANSPARENT)
            setStroke(strokeW, borderColor)
            setSize(trackW, trackH)
        }

        val track = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), trackOn)
            addState(intArrayOf(), trackOff)
        }

        switch.trackDrawable = track
        switch.thumbDrawable = thumb
        switch.switchMinWidth = trackW
        switch.showText = false
        switch.thumbTextPadding = (2 * density).toInt()
    }

    // ---- Background Mode ----

    private fun setupBackgroundMode() {
        val options = listOf(
            getString(R.string.settings_bg_none),
            getString(R.string.settings_bg_full_screen)
        )
        val adapter = ArrayAdapter(this, R.layout.spinner_item, options)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.bgModeSpinner.adapter = adapter

        val current = runBlocking { prefs.bgMode.first() }
        binding.bgModeSpinner.setSelection(if (current == "full_screen") 1 else 0)

        binding.choosePhotoButton.visibility =
            if (current == "full_screen") View.VISIBLE else View.GONE

        binding.bgModeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val mode = if (pos == 1) "full_screen" else "none"
                binding.choosePhotoButton.visibility =
                    if (mode == "full_screen") View.VISIBLE else View.GONE
                lifecycleScope.launch { prefs.setBgMode(mode) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.choosePhotoButton.setOnClickListener {
            pendingImageTarget = "bg"
            launchImagePicker()
        }
    }

    // ---- Tile Transparency ----

    private fun setupTileTransparency() {
        val current = runBlocking { prefs.tileTransparency.first() }
        binding.tileTransparencySeekBar.progress = current

        binding.tileTransparencySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    lifecycleScope.launch { prefs.setTileTransparency(it.progress) }
                }
            }
        })
    }

    // ---- Show More Tiles ----

    private fun setupShowMoreTiles() {
        val current = runBlocking { prefs.showMoreTiles.first() }
        binding.showMoreTilesSwitch.isChecked = current

        binding.showMoreTilesSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { prefs.setShowMoreTiles(isChecked) }
        }
    }

    // ---- Tile Mode ----

    private fun setupTileMode() {
        val current = runBlocking { prefs.tileMode.first() }
        binding.tileModeSwitch.isChecked = current

        binding.tileModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingModes) return@setOnCheckedChangeListener
            isUpdatingModes = true
            lifecycleScope.launch {
                val fromMode = prefs.getActiveTileMode()
                val toMode = if (isChecked) "tile_mode" else "default"
                prefs.saveTileSizesForMode(fromMode)
                prefs.setTileMode(isChecked)
                if (isChecked) {
                    prefs.setMinimalTiles(false)
                    binding.minimalTilesSwitch.isChecked = false
                    prefs.setUsageTracking(false)
                    binding.usageTrackingSwitch.isChecked = false
                }
                prefs.loadTileSizesForMode(toMode)
                isUpdatingModes = false
            }
        }
    }

    // ---- Minimal Tiles ----

    private fun setupMinimalTiles() {
        val current = runBlocking { prefs.minimalTiles.first() }
        binding.minimalTilesSwitch.isChecked = current

        binding.minimalTilesSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingModes) return@setOnCheckedChangeListener
            isUpdatingModes = true
            lifecycleScope.launch {
                val fromMode = prefs.getActiveTileMode()
                val toMode = if (isChecked) "minimal_tiles" else "default"
                prefs.saveTileSizesForMode(fromMode)
                prefs.setMinimalTiles(isChecked)
                if (isChecked) {
                    prefs.setTileMode(false)
                    binding.tileModeSwitch.isChecked = false
                    prefs.setUsageTracking(false)
                    binding.usageTrackingSwitch.isChecked = false
                }
                prefs.loadTileSizesForMode(toMode)
                isUpdatingModes = false
            }
        }
    }

    // ---- Usage Tracking ----

    private fun setupUsageTracking() {
        val current = runBlocking { prefs.usageTracking.first() }
        binding.usageTrackingSwitch.isChecked = current

        binding.usageTrackingSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isUpdatingModes) return@setOnCheckedChangeListener
            isUpdatingModes = true
            lifecycleScope.launch {
                val fromMode = prefs.getActiveTileMode()
                val toMode = if (isChecked) "usage_tracking" else "default"
                prefs.saveTileSizesForMode(fromMode)
                prefs.setUsageTracking(isChecked)
                if (isChecked) {
                    prefs.setTileMode(false)
                    binding.tileModeSwitch.isChecked = false
                    prefs.setMinimalTiles(false)
                    binding.minimalTilesSwitch.isChecked = false
                }
                prefs.loadTileSizesForMode(toMode)
                isUpdatingModes = false
            }
        }
    }

    // ---- Theme ----

    private fun setupTheme() {
        val options = listOf(
            getString(R.string.settings_theme_dark),
            getString(R.string.settings_theme_light)
        )
        val adapter = ArrayAdapter(this, R.layout.spinner_item, options)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        binding.themeSpinner.adapter = adapter

        val current = runBlocking { prefs.themeMode.first() }
        binding.themeSpinner.setSelection(if (current == "light") 1 else 0)

        binding.themeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                val mode = if (pos == 1) "light" else "dark"
                val prev = runBlocking { prefs.themeMode.first() }
                if (mode != prev) {
                    lifecycleScope.launch {
                        prefs.setThemeMode(mode)
                        recreate()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // ---- Accent Colors ----

    private fun setupAccentColors() {
        val colorValues = resources.getStringArray(R.array.accent_color_values).toList()
        val currentHex = runBlocking { prefs.accentColor.first() }

        val accentAdapter = AccentColorAdapter(colorValues, currentHex) { hex ->
            lifecycleScope.launch {
                prefs.setAccentColor(hex)
            }
        }

        binding.accentColorGrid.layoutManager = GridLayoutManager(this, 5)
        binding.accentColorGrid.adapter = accentAdapter
    }

    // ---- Lock Screen ----

    private fun setupLockScreen() {
        binding.chooseLockPhotoButton.setOnClickListener {
            pendingImageTarget = "lock"
            launchImagePicker()
        }
    }

    // ---- Image Picker ----

    private fun launchImagePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        pickImage.launch(intent)
    }

    private fun handleImagePicked(uri: Uri) {
        val target = pendingImageTarget ?: return
        pendingImageTarget = null

        lifecycleScope.launch {
            val fileName = if (target == "bg") "bg_image.jpg" else "lock_bg_image.jpg"
            val destFile = File(filesDir, fileName)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val path = destFile.absolutePath
                if (target == "bg") {
                    prefs.setBgImageUri(path)
                } else {
                    prefs.setLockBgUri(path)
                }
            } catch (_: Exception) {
                // Silently fail
            }
        }
    }
}
