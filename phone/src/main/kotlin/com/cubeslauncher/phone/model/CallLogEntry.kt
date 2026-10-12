package com.cubeslauncher.phone.model

import android.provider.CallLog

data class CallLogEntry(
    val id: Long,
    val number: String,
    val contactName: String?,
    val callType: Int,
    val date: Long,
    val duration: Long,
    val features: Int = 0,
    val phoneAccountComponentName: String? = null,
    val phoneAccountId: String? = null
) {
    val isWhatsAppCall: Boolean
        get() = phoneAccountComponentName?.startsWith("com.whatsapp/") == true ||
                phoneAccountComponentName?.startsWith("com.whatsapp.w4b/") == true

    val isVideoCall: Boolean
        get() = (features and CallLog.Calls.FEATURES_VIDEO) != 0
}
