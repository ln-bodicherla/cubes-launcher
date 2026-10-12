package com.cubeslauncher.messages

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SmsManager
import com.cubeslauncher.messages.model.Conversation
import com.cubeslauncher.messages.model.Message

class MessagesRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver
    private val contactNameCache = mutableMapOf<String, String?>()

    fun readConversations(): List<Conversation> {
        val conversations = mutableListOf<Conversation>()
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.READ,
                    Telephony.Sms.TYPE
                ),
                null, null,
                "${Telephony.Sms.DATE} DESC"
            )

            val threadMap = linkedMapOf<Long, ConversationBuilder>()

            cursor?.let {
                val threadIdIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)

                while (it.moveToNext()) {
                    val threadId = it.getLong(threadIdIdx)
                    val address = it.getString(addressIdx) ?: continue
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val read = it.getInt(readIdx) == 1
                    val type = it.getInt(typeIdx)

                    val builder = threadMap.getOrPut(threadId) {
                        ConversationBuilder(
                            threadId = threadId,
                            address = address,
                            snippet = body,
                            date = date,
                            isDraft = type == Telephony.Sms.MESSAGE_TYPE_DRAFT
                        )
                    }
                    builder.messageCount++
                    if (!read) builder.unreadCount++
                    if (!builder.addresses.contains(address)) {
                        builder.addresses.add(address)
                    }
                }
            }

            for (builder in threadMap.values) {
                val contactName = resolveContactName(builder.address)
                conversations.add(
                    Conversation(
                        threadId = builder.threadId,
                        recipientAddresses = builder.addresses.toList(),
                        contactName = contactName,
                        snippet = builder.snippet,
                        date = builder.date,
                        messageCount = builder.messageCount,
                        unreadCount = builder.unreadCount,
                        isGroup = builder.addresses.size > 1,
                        isDraft = builder.isDraft
                    )
                )
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }
        return conversations
    }

    fun readMessages(threadId: Long): List<Message> {
        val messages = mutableListOf<Message>()

        // Read SMS messages
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ,
                    Telephony.Sms.STATUS
                ),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
            )
            cursor?.let {
                val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                val threadIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)
                val statusIdx = it.getColumnIndex(Telephony.Sms.STATUS)

                while (it.moveToNext()) {
                    val address = it.getString(addressIdx) ?: ""
                    messages.add(
                        Message(
                            id = it.getLong(idIdx),
                            threadId = it.getLong(threadIdx),
                            address = address,
                            contactName = resolveContactName(address),
                            body = it.getString(bodyIdx) ?: "",
                            date = it.getLong(dateIdx),
                            type = it.getInt(typeIdx),
                            read = it.getInt(readIdx) == 1,
                            isMms = false,
                            mmsImageUri = null,
                            deliveryStatus = it.getInt(statusIdx),
                            scheduledTime = null
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }

        // Read MMS messages for this thread
        var mmsCursor: Cursor? = null
        try {
            mmsCursor = contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms._ID,
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.MESSAGE_BOX,
                    Telephony.Mms.READ,
                    Telephony.Mms.STATUS
                ),
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Mms.DATE} ASC"
            )
            mmsCursor?.let {
                val idIdx = it.getColumnIndex(Telephony.Mms._ID)
                val threadIdx = it.getColumnIndex(Telephony.Mms.THREAD_ID)
                val dateIdx = it.getColumnIndex(Telephony.Mms.DATE)
                val boxIdx = it.getColumnIndex(Telephony.Mms.MESSAGE_BOX)
                val readIdx = it.getColumnIndex(Telephony.Mms.READ)
                val statusIdx = it.getColumnIndex(Telephony.Mms.STATUS)

                while (it.moveToNext()) {
                    val mmsId = it.getLong(idIdx)
                    val mmsBody = readMmsTextPart(mmsId)
                    val mmsImage = readMmsImageUri(mmsId)
                    val mmsAddress = readMmsAddress(mmsId)
                    val box = it.getInt(boxIdx)
                    val type = when (box) {
                        Telephony.Mms.MESSAGE_BOX_INBOX -> Telephony.Sms.MESSAGE_TYPE_INBOX
                        Telephony.Mms.MESSAGE_BOX_SENT -> Telephony.Sms.MESSAGE_TYPE_SENT
                        Telephony.Mms.MESSAGE_BOX_DRAFTS -> Telephony.Sms.MESSAGE_TYPE_DRAFT
                        Telephony.Mms.MESSAGE_BOX_OUTBOX -> Telephony.Sms.MESSAGE_TYPE_OUTBOX
                        else -> Telephony.Sms.MESSAGE_TYPE_INBOX
                    }

                    messages.add(
                        Message(
                            id = mmsId,
                            threadId = it.getLong(threadIdx),
                            address = mmsAddress,
                            contactName = resolveContactName(mmsAddress),
                            body = mmsBody,
                            date = it.getLong(dateIdx) * 1000, // MMS date is in seconds
                            type = type,
                            read = it.getInt(readIdx) == 1,
                            isMms = true,
                            mmsImageUri = mmsImage,
                            deliveryStatus = it.getInt(statusIdx),
                            scheduledTime = null
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        } finally {
            mmsCursor?.close()
        }

        return messages.sortedBy { it.date }
    }

    private fun readMmsTextPart(mmsId: Long): String {
        var text = ""
        var cursor: Cursor? = null
        try {
            val uri = Uri.parse("content://mms/${mmsId}/part")
            cursor = contentResolver.query(
                uri,
                arrayOf("_id", "ct", "text"),
                "ct = ?",
                arrayOf("text/plain"),
                null
            )
            cursor?.let {
                if (it.moveToFirst()) {
                    val textIdx = it.getColumnIndex("text")
                    if (textIdx >= 0) {
                        text = it.getString(textIdx) ?: ""
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return text
    }

    private fun readMmsImageUri(mmsId: Long): String? {
        var cursor: Cursor? = null
        try {
            val uri = Uri.parse("content://mms/${mmsId}/part")
            cursor = contentResolver.query(
                uri,
                arrayOf("_id", "ct"),
                "ct LIKE ?",
                arrayOf("image/%"),
                null
            )
            cursor?.let {
                if (it.moveToFirst()) {
                    val idIdx = it.getColumnIndex("_id")
                    val partId = it.getLong(idIdx)
                    return "content://mms/part/$partId"
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return null
    }

    private fun readMmsAddress(mmsId: Long): String {
        var address = ""
        var cursor: Cursor? = null
        try {
            val uri = Uri.parse("content://mms/${mmsId}/addr")
            cursor = contentResolver.query(
                uri,
                arrayOf("address", "type"),
                "type = 137", // FROM address
                null,
                null
            )
            cursor?.let {
                if (it.moveToFirst()) {
                    val addrIdx = it.getColumnIndex("address")
                    address = it.getString(addrIdx) ?: ""
                }
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        return address
    }

    fun sendSms(address: String, body: String) {
        val smsManager = SmsManager.getDefault()
        val sentIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent("SMS_SENT"),
            PendingIntent.FLAG_IMMUTABLE
        )
        val deliveryIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent("SMS_DELIVERED"),
            PendingIntent.FLAG_IMMUTABLE
        )

        val parts = smsManager.divideMessage(body)
        if (parts.size == 1) {
            smsManager.sendTextMessage(address, null, body, sentIntent, deliveryIntent)
        } else {
            val sentIntents = ArrayList<PendingIntent>(parts.size)
            val deliveryIntents = ArrayList<PendingIntent>(parts.size)
            for (i in parts.indices) {
                sentIntents.add(sentIntent)
                deliveryIntents.add(deliveryIntent)
            }
            smsManager.sendMultipartTextMessage(address, null, parts, sentIntents, deliveryIntents)
        }

        // Write to sent box
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
        }
        try {
            contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (_: SecurityException) {
        }
    }

    fun deleteMessage(id: Long, isMms: Boolean) {
        try {
            val uri = if (isMms) {
                Uri.parse("content://mms/$id")
            } else {
                Uri.parse("content://sms/$id")
            }
            contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
    }

    fun deleteConversation(threadId: Long) {
        try {
            contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
            contentResolver.delete(
                Telephony.Mms.CONTENT_URI,
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
        } catch (_: Exception) {
        }
    }

    fun markThreadRead(threadId: Long) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 1)
            }
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
        } catch (_: Exception) {
        }
    }

    fun markThreadUnread(threadId: Long) {
        try {
            val values = ContentValues().apply {
                put(Telephony.Sms.READ, 0)
            }
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX.toString())
            )
        } catch (_: Exception) {
        }
    }

    fun saveDraft(threadId: Long, address: String, body: String) {
        deleteDraft(threadId)
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_DRAFT)
            put(Telephony.Sms.THREAD_ID, threadId)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
        }
        try {
            contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
        } catch (_: SecurityException) {
        }
    }

    fun getDraft(threadId: Long): String? {
        var draft: String? = null
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.BODY),
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_DRAFT.toString()),
                "${Telephony.Sms.DATE} DESC"
            )
            if (cursor?.moveToFirst() == true) {
                draft = cursor.getString(0)
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }
        return draft
    }

    fun deleteDraft(threadId: Long) {
        try {
            contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.TYPE} = ?",
                arrayOf(threadId.toString(), Telephony.Sms.MESSAGE_TYPE_DRAFT.toString())
            )
        } catch (_: Exception) {
        }
    }

    fun searchMessages(query: String): List<Message> {
        val messages = mutableListOf<Message>()
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(
                    Telephony.Sms._ID,
                    Telephony.Sms.THREAD_ID,
                    Telephony.Sms.ADDRESS,
                    Telephony.Sms.BODY,
                    Telephony.Sms.DATE,
                    Telephony.Sms.TYPE,
                    Telephony.Sms.READ,
                    Telephony.Sms.STATUS
                ),
                "${Telephony.Sms.BODY} LIKE ?",
                arrayOf("%$query%"),
                "${Telephony.Sms.DATE} DESC"
            )
            cursor?.let {
                val idIdx = it.getColumnIndex(Telephony.Sms._ID)
                val threadIdx = it.getColumnIndex(Telephony.Sms.THREAD_ID)
                val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndex(Telephony.Sms.TYPE)
                val readIdx = it.getColumnIndex(Telephony.Sms.READ)
                val statusIdx = it.getColumnIndex(Telephony.Sms.STATUS)

                while (it.moveToNext()) {
                    val address = it.getString(addressIdx) ?: ""
                    messages.add(
                        Message(
                            id = it.getLong(idIdx),
                            threadId = it.getLong(threadIdx),
                            address = address,
                            contactName = resolveContactName(address),
                            body = it.getString(bodyIdx) ?: "",
                            date = it.getLong(dateIdx),
                            type = it.getInt(typeIdx),
                            read = it.getInt(readIdx) == 1,
                            isMms = false,
                            mmsImageUri = null,
                            deliveryStatus = it.getInt(statusIdx),
                            scheduledTime = null
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
        } finally {
            cursor?.close()
        }
        return messages
    }

    fun resolveContactName(address: String): String? {
        if (address.isBlank()) return null
        contactNameCache[address]?.let { return it }

        var name: String? = null
        var cursor: Cursor? = null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(address)
            )
            cursor = contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )
            if (cursor?.moveToFirst() == true) {
                name = cursor.getString(0)
            }
        } catch (_: Exception) {
        } finally {
            cursor?.close()
        }
        contactNameCache[address] = name
        return name
    }

    private class ConversationBuilder(
        val threadId: Long,
        val address: String,
        val snippet: String,
        val date: Long,
        val isDraft: Boolean,
        var messageCount: Int = 0,
        var unreadCount: Int = 0,
        val addresses: MutableList<String> = mutableListOf(address)
    )
}
