package com.cubeslauncher.app.statusbar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

data class StatusBarState(
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val wifiLevel: Int = -1, // -1 = disconnected, 0-4 = strength
    val signalLevel: Int = -1, // -1 = no service, 0-4 = strength
    val clockText: String = ""
)

class StatusBarManager(private val context: Context) {

    private val _state = MutableStateFlow(StatusBarState())
    val state: StateFlow<StatusBarState> = _state.asStateFlow()

    private var clockTimer: Timer? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    @Suppress("DEPRECATION")
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: Any? = null

    fun start() {
        startClockUpdates()
        registerBatteryReceiver()
        registerConnectivityCallback()
        registerSignalStrengthListener()
    }

    fun stop() {
        clockTimer?.cancel()
        clockTimer = null

        batteryReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        batteryReceiver = null

        connectivityCallback?.let {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(it)
        }
        connectivityCallback = null

        unregisterSignalListener()
    }

    private fun startClockUpdates() {
        updateClock()
        clockTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    updateClock()
                }
            }, 1000, 1000)
        }
    }

    private fun updateClock() {
        val format = SimpleDateFormat("h:mm", Locale.getDefault())
        val time = format.format(Date())
        _state.value = _state.value.copy(clockText = time)
    }

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
                val pct = (level * 100) / scale
                _state.value = _state.value.copy(batteryLevel = pct, isCharging = isCharging)
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(batteryReceiver, filter)
    }

    private fun registerConnectivityCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        connectivityCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val wifiLevel = if (hasWifi && wifiManager != null) {
                    @Suppress("DEPRECATION")
                    val rssi = wifiManager.connectionInfo.rssi
                    WifiManager.calculateSignalLevel(rssi, 5)
                } else {
                    -1
                }
                _state.value = _state.value.copy(wifiLevel = wifiLevel)
            }

            override fun onLost(network: Network) {
                _state.value = _state.value.copy(wifiLevel = -1)
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        cm.registerNetworkCallback(request, connectivityCallback!!)

        // Initial WiFi state
        val activeNetwork = cm.activeNetwork
        val activeCaps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        if (activeCaps != null && activeCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && wifiManager != null) {
            @Suppress("DEPRECATION")
            val rssi = wifiManager.connectionInfo.rssi
            _state.value = _state.value.copy(wifiLevel = WifiManager.calculateSignalLevel(rssi, 5))
        }
    }

    @Suppress("DEPRECATION")
    private fun registerSignalStrengthListener() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerTelephonyCallbackS(tm)
        } else {
            phoneStateListener = object : PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                    val level = signalStrength?.level ?: -1
                    _state.value = _state.value.copy(signalLevel = level)
                }
            }
            try {
                tm.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS)
            } catch (_: SecurityException) {
                _state.value = _state.value.copy(signalLevel = -1)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallbackS(tm: TelephonyManager) {
        val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                _state.value = _state.value.copy(signalLevel = signalStrength.level)
            }
        }
        telephonyCallback = callback
        try {
            tm.registerTelephonyCallback(context.mainExecutor, callback)
        } catch (_: SecurityException) {
            _state.value = _state.value.copy(signalLevel = -1)
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterSignalListener() {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                tm?.unregisterTelephonyCallback(it)
            }
        } else {
            phoneStateListener?.let {
                tm?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
        telephonyCallback = null
        phoneStateListener = null
    }
}
