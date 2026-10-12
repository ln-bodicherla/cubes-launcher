package com.cubeslauncher.messages.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.cubeslauncher.messages.MessagesRepository
import com.cubeslauncher.messages.notification.MessageNotificationHelper

class DirectReplyReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val KEY_TEXT_REPLY = "key_text_reply"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(KEY_TEXT_REPLY)?.toString() ?: return
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        // Send the reply
        val repository = MessagesRepository(context)
        repository.sendSms(address, replyText)

        // Update the notification to show the reply was sent
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        // Show updated notification
        MessageNotificationHelper.showReplyConfirmation(context, address, replyText, notificationId)
    }
}
