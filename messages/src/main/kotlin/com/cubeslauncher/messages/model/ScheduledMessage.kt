package com.cubeslauncher.messages.model

data class ScheduledMessage(
    val id: String,
    val address: String,
    val body: String,
    val scheduledTimeMillis: Long,
    val createdAt: Long
)
