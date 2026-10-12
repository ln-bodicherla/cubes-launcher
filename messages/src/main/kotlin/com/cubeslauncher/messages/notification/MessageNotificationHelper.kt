package com.cubeslauncher.messages.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.cubeslauncher.messages.R
import com.cubeslauncher.messages.ConversationActivity
import com.cubeslauncher.messages.MessagesRepository
import com.cubeslauncher.messages.receiver.DirectReplyReceiver

object MessageNotificationHelper {

    private const val CHANNEL_ID = "cubes_messages"
    private const val CHANNEL_NAME = "Messages"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.messages_notification_channel)
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showMessageNotification(context: Context, address: String, body: String) {
        createNotificationChannel(context)

        val notificationId = address.hashCode()

        // Resolve contact name
        val repository = MessagesRepository(context)
        val contactName = repository.resolveContactName(address) ?: address

        // Content intent - open conversation
        val contentIntent = Intent(context, ConversationActivity::class.java).apply {
            putExtra(ConversationActivity.EXTRA_ADDRESS, address)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Inline reply action
        val remoteInput = RemoteInput.Builder(DirectReplyReceiver.KEY_TEXT_REPLY)
            .setLabel(context.getString(R.string.messages_notification_reply))
            .build()

        val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
            putExtra(DirectReplyReceiver.EXTRA_ADDRESS, address)
            putExtra(DirectReplyReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_messages_send,
            context.getString(R.string.messages_notification_reply),
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        // Build notification with MessagingStyle
        val sender = Person.Builder().setName(contactName).build()
        val messagingStyle = NotificationCompat.MessagingStyle(
            Person.Builder().setName("Me").build()
        ).addMessage(body, System.currentTimeMillis(), sender)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_message)
            .setStyle(messagingStyle)
            .setContentIntent(contentPendingIntent)
            .addAction(replyAction)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Missing POST_NOTIFICATIONS permission on API 33+
        }
    }

    fun showReplyConfirmation(context: Context, address: String, replyText: String, notificationId: Int) {
        createNotificationChannel(context)

        val repository = MessagesRepository(context)
        val contactName = repository.resolveContactName(address) ?: address

        val me = Person.Builder().setName("Me").build()
        val sender = Person.Builder().setName(contactName).build()
        val messagingStyle = NotificationCompat.MessagingStyle(me)
            .addMessage(replyText, System.currentTimeMillis(), me)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_phone_message)
            .setStyle(messagingStyle)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
        }
    }
}
