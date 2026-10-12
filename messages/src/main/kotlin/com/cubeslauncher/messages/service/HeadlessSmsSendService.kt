package com.cubeslauncher.messages.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.cubeslauncher.messages.MessagesRepository

class HeadlessSmsSendService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val uri = intent.data ?: run {
                stopSelf()
                return START_NOT_STICKY
            }
            val address = uri.schemeSpecificPart?.replace("-", "")?.replace(" ", "")
            val body = intent.getStringExtra(Intent.EXTRA_TEXT)

            if (!address.isNullOrEmpty() && !body.isNullOrEmpty()) {
                val repository = MessagesRepository(this)
                repository.sendSms(address, body)
            }
        }
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
