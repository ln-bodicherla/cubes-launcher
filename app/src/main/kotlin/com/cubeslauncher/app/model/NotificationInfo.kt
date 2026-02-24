package com.cubeslauncher.app.model

import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon

data class NotificationInfo(
    val key: String,
    val appName: String,
    val appIcon: Drawable?,
    val smallIcon: Icon?,
    val title: String,
    val body: String,
    val timestamp: Long,
    val packageName: String
)
