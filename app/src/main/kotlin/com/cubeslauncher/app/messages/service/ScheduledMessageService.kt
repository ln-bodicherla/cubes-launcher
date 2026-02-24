package com.cubeslauncher.app.messages.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.cubeslauncher.app.messages.MessagesRepository
import com.cubeslauncher.app.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScheduledMessageService : Service() {

    companion object {
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_ADDRESS = "extra_address"
        const val EXTRA_BODY = "extra_body"

        fun scheduleMessage(
            context: Context,
            messageId: String,
            address: String,
            body: String,
            triggerAtMillis: Long
        ) {
            val intent = Intent(context, ScheduledMessageService::class.java).apply {
                putExtra(EXTRA_MESSAGE_ID, messageId)
                putExtra(EXTRA_ADDRESS, address)
                putExtra(EXTRA_BODY, body)
            }
            val pendingIntent = PendingIntent.getService(
                context,
                messageId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val messageId = intent?.getStringExtra(EXTRA_MESSAGE_ID)
        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        val body = intent?.getStringExtra(EXTRA_BODY)

        if (messageId != null && address != null && body != null) {
            serviceScope.launch {
                val repository = MessagesRepository(this@ScheduledMessageService)
                repository.sendSms(address, body)

                val preferencesManager = PreferencesManager(this@ScheduledMessageService)
                preferencesManager.removeScheduledMessage(messageId)

                stopSelf(startId)
            }
        } else {
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
