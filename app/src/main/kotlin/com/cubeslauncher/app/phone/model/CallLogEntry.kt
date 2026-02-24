package com.cubeslauncher.app.phone.model

data class CallLogEntry(
    val id: Long,
    val number: String,
    val contactName: String?,
    val callType: Int,
    val date: Long,
    val duration: Long
)
