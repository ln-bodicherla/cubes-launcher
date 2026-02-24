package com.cubeslauncher.app.phone.model

import android.net.Uri

data class SpeedDialContact(
    val contactId: Long,
    val name: String,
    val phoneNumber: String,
    val photoUri: Uri?
)
