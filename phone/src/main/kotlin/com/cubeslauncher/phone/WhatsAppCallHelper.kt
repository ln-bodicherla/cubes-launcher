package com.cubeslauncher.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import com.cubeslauncher.phone.R

object WhatsAppCallHelper {

    private const val MIME_VOICE_CALL = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
    private const val MIME_VIDEO_CALL = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
    private const val WHATSAPP_PACKAGE = "com.whatsapp"

    fun makeWhatsAppCall(context: Context, phoneNumber: String, video: Boolean) {
        val mimeType = if (video) MIME_VIDEO_CALL else MIME_VOICE_CALL
        val dataId = findWhatsAppDataId(context, phoneNumber, mimeType)

        if (dataId != null) {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.android.contacts/data/$dataId"),
                    mimeType
                )
                setPackage(WHATSAPP_PACKAGE)
            }
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) { }
        }

        // Fallback: open WhatsApp chat for this number, user can call from there
        val cleanNumber = phoneNumber.replace(Regex("[^\\d+]"), "")
        val chatIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://wa.me/$cleanNumber")
        }
        try {
            context.startActivity(chatIntent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.phone_whatsapp_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    fun makeWhatsAppCallByName(context: Context, callerName: String, video: Boolean) {
        val phoneNumber = findPhoneNumberByName(context, callerName)
        if (phoneNumber != null) {
            makeWhatsAppCall(context, phoneNumber, video)
        } else {
            Toast.makeText(context, R.string.phone_whatsapp_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    private fun findPhoneNumberByName(context: Context, displayName: String): String? {
        return try {
            val contactId = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?",
                arrayOf(displayName),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            } ?: return null

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

    fun isWhatsAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(WHATSAPP_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findWhatsAppDataId(context: Context, phoneNumber: String, mimeType: String): Long? {
        // Find contact ID by phone number
        val contactId = findContactId(context, phoneNumber) ?: return null

        // Find WhatsApp data row for this contact
        return try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data._ID),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId.toString(), mimeType),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findContactId(context: Context, phoneNumber: String): Long? {
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        return try {
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup._ID),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
