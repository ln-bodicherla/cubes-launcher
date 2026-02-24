package com.cubeslauncher.app.phone

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CubesInCallService : InCallService() {

    companion object {
        var instance: CubesInCallService? = null
            private set

        private val _currentCall = MutableStateFlow<Call?>(null)
        val currentCall: StateFlow<Call?> = _currentCall.asStateFlow()

        private val _callState = MutableStateFlow(CallUiState())
        val callStateFlow: StateFlow<CallUiState> = _callState.asStateFlow()

        fun updateState(state: CallUiState) {
            _callState.value = state
        }
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            updateCallState(call, state)
            if (state == Call.STATE_DISCONNECTED) {
                call.unregisterCallback(this)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _currentCall.value = null
        _callState.value = CallUiState()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        _currentCall.value = call
        call.registerCallback(callCallback)

        val state = call.state
        updateCallState(call, state)

        val intent = Intent(this, InCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        _currentCall.value = null
        _callState.value = CallUiState(state = CallUiState.State.DISCONNECTED)
    }

    private fun updateCallState(call: Call, state: Int) {
        val handle = call.details?.handle
        val number = handle?.schemeSpecificPart ?: ""
        val systemName = call.details?.callerDisplayName ?: ""

        // Resolve contact name: try system-provided name first, then look up in contacts
        val callerName = systemName.ifEmpty {
            if (number.isNotEmpty()) resolveContactName(number) ?: "" else ""
        }

        val uiState = when (state) {
            Call.STATE_RINGING -> CallUiState.State.RINGING
            Call.STATE_DIALING, Call.STATE_CONNECTING -> CallUiState.State.DIALING
            Call.STATE_ACTIVE -> CallUiState.State.ACTIVE
            Call.STATE_HOLDING -> CallUiState.State.HOLDING
            Call.STATE_DISCONNECTED -> CallUiState.State.DISCONNECTED
            else -> CallUiState.State.CONNECTING
        }

        _callState.value = CallUiState(
            state = uiState,
            number = number,
            callerName = callerName,
            connectTimeMs = if (uiState == CallUiState.State.ACTIVE)
                _callState.value.connectTimeMs.takeIf { it > 0 } ?: System.currentTimeMillis()
            else _callState.value.connectTimeMs
        )
    }

    private fun resolveContactName(number: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (_: SecurityException) {
            null
        }
    }
}

data class CallUiState(
    val state: State = State.IDLE,
    val number: String = "",
    val callerName: String = "",
    val connectTimeMs: Long = 0L
) {
    enum class State {
        IDLE,
        RINGING,
        DIALING,
        CONNECTING,
        ACTIVE,
        HOLDING,
        DISCONNECTED
    }
}
