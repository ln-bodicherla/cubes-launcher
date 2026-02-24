package com.cubeslauncher.app.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

class SimSelectionHelper(private val context: Context) {

    data class SimInfo(
        val subscriptionId: Int,
        val label: String,
        val phoneAccountHandle: PhoneAccountHandle?
    )

    fun getAvailableSims(): List<SimInfo> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val sims = mutableListOf<SimInfo>()
        try {
            val subscriptionManager = context.getSystemService(SubscriptionManager::class.java)
            val telecomManager = context.getSystemService(TelecomManager::class.java)
            val activeSubscriptions: List<SubscriptionInfo> =
                subscriptionManager?.activeSubscriptionInfoList ?: return emptyList()

            val callCapableAccounts = telecomManager?.callCapablePhoneAccounts ?: emptyList()

            for ((index, subInfo) in activeSubscriptions.withIndex()) {
                val label = subInfo.displayName?.toString()
                    ?: context.getString(android.R.string.unknownName)
                val simLabel = if (label.isNotEmpty()) label else "SIM ${index + 1}"

                // Try to find matching PhoneAccountHandle
                val phoneAccount = callCapableAccounts.getOrNull(index)

                sims.add(
                    SimInfo(
                        subscriptionId = subInfo.subscriptionId,
                        label = simLabel,
                        phoneAccountHandle = phoneAccount
                    )
                )
            }
        } catch (_: SecurityException) {
            // Permission not granted
        } catch (_: Exception) {
            // Device may not support dual SIM
        }

        return sims
    }

    fun isDualSim(): Boolean = getAvailableSims().size > 1
}
