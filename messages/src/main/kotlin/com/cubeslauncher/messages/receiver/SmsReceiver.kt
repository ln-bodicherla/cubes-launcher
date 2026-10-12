package com.cubeslauncher.messages.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.cubeslauncher.messages.notification.MessageNotificationHelper

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // Group messages by originating address (multi-part SMS)
        val grouped = messages.groupBy { it.originatingAddress ?: "" }

        for ((address, parts) in grouped) {
            if (address.isEmpty()) continue

            val body = parts.joinToString("") { it.messageBody ?: "" }

            // Write to inbox
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 0)
            }

            try {
                context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            } catch (_: SecurityException) {
            }

            // Show notification
            MessageNotificationHelper.showMessageNotification(context, address, body)
        }

        abortBroadcast()
    }
}
