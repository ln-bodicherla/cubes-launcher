package com.cubeslauncher.messages.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cubeslauncher.messages.notification.MessageNotificationHelper

class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.WAP_PUSH_DELIVER") return

        // MMS handling: the system writes MMS PDUs to the telephony provider.
        // Show a notification for the incoming MMS.
        val address = "MMS"
        val body = "New multimedia message"

        MessageNotificationHelper.showMessageNotification(context, address, body)

        abortBroadcast()
    }
}
