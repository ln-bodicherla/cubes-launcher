package com.cubeslauncher.keyboard

import android.content.ClipboardManager
import android.content.Context

class KeyboardClipboardManager(context: Context) {

    private val systemClipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val history = mutableListOf<String>()

    init {
        systemClipboard.addPrimaryClipChangedListener {
            val text = systemClipboard.primaryClip?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrBlank()) {
                history.remove(text)
                history.add(0, text)
                if (history.size > 20) history.removeAt(history.size - 1)
            }
        }
    }

    fun getHistory(): List<String> = history.toList()

    fun clear() = history.clear()
}
