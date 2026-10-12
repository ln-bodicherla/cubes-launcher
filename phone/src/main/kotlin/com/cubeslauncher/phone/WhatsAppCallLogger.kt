package com.cubeslauncher.phone

import android.app.Notification
import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.service.notification.StatusBarNotification
import com.cubeslauncher.phone.model.CallLogEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WhatsAppCallLogger private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: WhatsAppCallLogger? = null

        fun getInstance(context: Context): WhatsAppCallLogger {
            return instance ?: synchronized(this) {
                instance ?: WhatsAppCallLogger(context.applicationContext).also { instance = it }
            }
        }

        private const val FILE_NAME = "whatsapp_calls.json"
        private const val COMPONENT_NAME = "com.whatsapp/notification"
    }

    private data class ActiveCall(
        val callerName: String,
        val isVideo: Boolean,
        val isIncoming: Boolean,
        val startTime: Long
    )

    private val activeCallMap = mutableMapOf<String, ActiveCall>()

    fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isWhatsAppCallNotification(sbn)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val callerName = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""

        val isVideo = text.contains("video")
        val isIncoming = text.contains("incoming") || text.contains("ringing")

        activeCallMap[sbn.key] = ActiveCall(
            callerName = callerName,
            isVideo = isVideo,
            isIncoming = isIncoming,
            startTime = System.currentTimeMillis()
        )
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!sbn.packageName.startsWith("com.whatsapp")) return

        val activeCall = activeCallMap.remove(sbn.key) ?: return

        val duration = (System.currentTimeMillis() - activeCall.startTime) / 1000
        val callType = when {
            activeCall.isIncoming && duration < 2 -> CallLog.Calls.MISSED_TYPE
            activeCall.isIncoming -> CallLog.Calls.INCOMING_TYPE
            else -> CallLog.Calls.OUTGOING_TYPE
        }

        val phoneNumber = resolvePhoneNumber(activeCall.callerName)

        val entry = JSONObject().apply {
            put("callerName", activeCall.callerName)
            put("number", phoneNumber ?: "")
            put("isVideo", activeCall.isVideo)
            put("callType", callType)
            put("date", activeCall.startTime)
            put("duration", duration)
        }

        appendEntry(entry)
    }

    fun getEntries(): List<CallLogEntry> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()

        return try {
            val json = file.readText()
            val array = JSONArray(json)
            val entries = mutableListOf<CallLogEntry>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                entries.add(
                    CallLogEntry(
                        id = -(i + 1).toLong(),
                        number = obj.optString("number", ""),
                        contactName = if (obj.has("callerName")) obj.getString("callerName") else null,
                        callType = obj.optInt("callType", CallLog.Calls.INCOMING_TYPE),
                        date = obj.optLong("date", 0),
                        duration = obj.optLong("duration", 0),
                        features = if (obj.optBoolean("isVideo", false)) CallLog.Calls.FEATURES_VIDEO else 0,
                        phoneAccountComponentName = COMPONENT_NAME
                    )
                )
            }

            entries
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isWhatsAppCallNotification(sbn: StatusBarNotification): Boolean {
        if (!sbn.packageName.startsWith("com.whatsapp")) return false
        val notification = sbn.notification ?: return false
        return notification.category == Notification.CATEGORY_CALL
    }

    private fun resolvePhoneNumber(callerName: String): String? {
        return try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?",
                arrayOf(callerName),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val contactId = cursor.getLong(0)
                    getPhoneForContact(contactId)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getPhoneForContact(contactId: Long): String? {
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    @Synchronized
    private fun appendEntry(entry: JSONObject) {
        val file = File(context.filesDir, FILE_NAME)
        val array = try {
            if (file.exists()) JSONArray(file.readText()) else JSONArray()
        } catch (_: Exception) {
            JSONArray()
        }

        array.put(entry)

        // Keep only last 500 entries
        if (array.length() > 500) {
            val trimmed = JSONArray()
            for (i in (array.length() - 500) until array.length()) {
                trimmed.put(array.getJSONObject(i))
            }
            file.writeText(trimmed.toString())
        } else {
            file.writeText(array.toString())
        }
    }
}
