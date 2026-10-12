package com.cubeslauncher.launcher.statusbar

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import com.cubeslauncher.launcher.R
import com.cubeslauncher.launcher.databinding.ViewNotificationPanelBinding

data class QuickSettingTile(
    val id: String,
    val label: String,
    val iconRes: Int,
    val isActive: () -> Boolean,
    val onToggle: () -> Unit
)

class QuickSettingsController(private val context: Context) {

    private var tiles: List<QuickSettingTile> = emptyList()
    private var tileViews: MutableMap<String, View> = mutableMapOf()
    private var flashlightOn = false
    private var cameraManager: CameraManager? = null
    private var cameraId: String? = null

    init {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        cameraId = try { cameraManager?.cameraIdList?.firstOrNull() } catch (_: Exception) { null }
        buildTiles()
    }

    private fun buildTiles() {
        tiles = listOf(
            QuickSettingTile(
                id = "wifi",
                label = context.getString(R.string.qs_wifi),
                iconRes = R.drawable.ic_qs_wifi,
                isActive = { isWifiEnabled() },
                onToggle = { toggleWifi() }
            ),
            QuickSettingTile(
                id = "bluetooth",
                label = context.getString(R.string.qs_bluetooth),
                iconRes = R.drawable.ic_qs_bluetooth,
                isActive = { isBluetoothEnabled() },
                onToggle = { toggleBluetooth() }
            ),
            QuickSettingTile(
                id = "flashlight",
                label = context.getString(R.string.qs_flashlight),
                iconRes = R.drawable.ic_qs_flashlight,
                isActive = { flashlightOn },
                onToggle = { toggleFlashlight() }
            ),
            QuickSettingTile(
                id = "airplane",
                label = context.getString(R.string.qs_airplane),
                iconRes = R.drawable.ic_qs_airplane,
                isActive = { isAirplaneModeOn() },
                onToggle = { openAirplaneSettings() }
            ),
            QuickSettingTile(
                id = "location",
                label = context.getString(R.string.qs_location),
                iconRes = R.drawable.ic_qs_location,
                isActive = { isLocationEnabled() },
                onToggle = { openLocationSettings() }
            ),
            QuickSettingTile(
                id = "rotation",
                label = context.getString(R.string.qs_rotation),
                iconRes = R.drawable.ic_qs_rotation,
                isActive = { isAutoRotateEnabled() },
                onToggle = { toggleAutoRotate() }
            ),
            QuickSettingTile(
                id = "data",
                label = context.getString(R.string.qs_mobile_data),
                iconRes = R.drawable.ic_qs_data,
                isActive = { false },
                onToggle = { openDataSettings() }
            ),
            QuickSettingTile(
                id = "quiet",
                label = context.getString(R.string.qs_quiet_hours),
                iconRes = R.drawable.ic_qs_quiet,
                isActive = { isDndEnabled() },
                onToggle = { openDndSettings() }
            )
        )
    }

    fun setup(binding: ViewNotificationPanelBinding) {
        val inflater = LayoutInflater.from(context)

        // Row 1: first 4 tiles
        binding.quickSettingsRow1.removeAllViews()
        for (i in 0 until minOf(4, tiles.size)) {
            val tile = tiles[i]
            val tileView = inflater.inflate(R.layout.item_quick_setting_tile, binding.quickSettingsRow1, false)
            bindTileView(tileView, tile)
            binding.quickSettingsRow1.addView(tileView)
            tileViews[tile.id] = tileView
        }

        // Row 2: next 4 tiles
        binding.quickSettingsRow2.removeAllViews()
        for (i in 4 until minOf(8, tiles.size)) {
            val tile = tiles[i]
            val tileView = inflater.inflate(R.layout.item_quick_setting_tile, binding.quickSettingsRow2, false)
            bindTileView(tileView, tile)
            binding.quickSettingsRow2.addView(tileView)
            tileViews[tile.id] = tileView
        }

        // Brightness slider
        setupBrightnessSlider(binding.brightnessSlider)
    }

    private fun bindTileView(view: View, tile: QuickSettingTile) {
        val icon = view.findViewById<ImageView>(R.id.tileIcon)
        val label = view.findViewById<TextView>(R.id.tileLabel)
        val bg = view.findViewById<FrameLayout>(R.id.tileBackground)

        icon.setImageResource(tile.iconRes)
        label.text = tile.label

        updateTileAppearance(bg, icon, tile.isActive())

        view.setOnClickListener {
            tile.onToggle()
            // Refresh after a short delay to let system state settle
            view.postDelayed({
                updateTileAppearance(bg, icon, tile.isActive())
            }, 300)
        }
    }

    private fun updateTileAppearance(bg: FrameLayout, icon: ImageView, active: Boolean) {
        if (active) {
            bg.setBackgroundColor(context.getColor(R.color.wp_accent_blue))
            icon.setColorFilter(context.getColor(R.color.wp_white))
        } else {
            bg.setBackgroundColor(context.getColor(R.color.wp_white_10))
            icon.setColorFilter(context.getColor(R.color.wp_white_70))
        }
    }

    fun refreshStates() {
        for (tile in tiles) {
            val view = tileViews[tile.id] ?: continue
            val bg = view.findViewById<FrameLayout>(R.id.tileBackground)
            val icon = view.findViewById<ImageView>(R.id.tileIcon)
            updateTileAppearance(bg, icon, tile.isActive())
        }
    }

    // --- Toggle / Query methods ---

    @Suppress("DEPRECATION")
    private fun isWifiEnabled(): Boolean {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wm?.isWifiEnabled == true
    }

    @Suppress("DEPRECATION")
    private fun toggleWifi() {
        // On Android 10+, we can't toggle WiFi directly, open settings instead
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val intent = Intent(Settings.Panel.ACTION_WIFI)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wm?.isWifiEnabled = !wm.isWifiEnabled
        }
    }

    private fun isBluetoothEnabled(): Boolean {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bm?.adapter?.isEnabled == true
    }

    @Suppress("MissingPermission")
    private fun toggleBluetooth() {
        // On Android 12+, we need BLUETOOTH_CONNECT permission. Open settings instead.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: return
            @Suppress("DEPRECATION")
            if (adapter.isEnabled) adapter.disable() else adapter.enable()
        }
    }

    private fun toggleFlashlight() {
        val camId = cameraId ?: return
        flashlightOn = !flashlightOn
        try {
            cameraManager?.setTorchMode(camId, flashlightOn)
        } catch (_: Exception) {
            flashlightOn = false
        }
    }

    private fun isAirplaneModeOn(): Boolean {
        return Settings.Global.getInt(
            context.contentResolver,
            Settings.Global.AIRPLANE_MODE_ON, 0
        ) != 0
    }

    private fun openAirplaneSettings() {
        val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun isAutoRotateEnabled(): Boolean {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION, 0
        ) == 1
    }

    private fun toggleAutoRotate() {
        try {
            val current = isAutoRotateEnabled()
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (current) 0 else 1
            )
        } catch (_: SecurityException) {
            // Need WRITE_SETTINGS permission
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private fun openDataSettings() {
        val intent = Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun isDndEnabled(): Boolean {
        return try {
            val zenMode = Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
            zenMode != 0
        } catch (_: Exception) {
            false
        }
    }

    private fun openDndSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
        } else {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun setupBrightnessSlider(seekBar: SeekBar) {
        // Get current brightness
        try {
            val brightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                128
            )
            seekBar.progress = brightness
        } catch (_: Exception) {}

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    try {
                        Settings.System.putInt(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                        )
                        Settings.System.putInt(
                            context.contentResolver,
                            Settings.System.SCREEN_BRIGHTNESS,
                            progress
                        )
                    } catch (_: SecurityException) {
                        // Need WRITE_SETTINGS permission
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }
}
