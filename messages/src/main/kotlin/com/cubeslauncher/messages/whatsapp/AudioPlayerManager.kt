package com.cubeslauncher.messages.whatsapp

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView

/**
 * Singleton manager for voice message and audio playback.
 * Handles play/pause/seek across different messages.
 */
object AudioPlayerManager {

    private var mediaPlayer: MediaPlayer? = null
    private var currentMessageId: String? = null
    private var handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    // Currently bound UI elements
    private var boundSeekBar: SeekBar? = null
    private var boundDuration: TextView? = null
    private var boundPlayPause: ImageButton? = null

    fun playOrPause(
        messageId: String,
        filePath: String,
        playPauseButton: ImageButton,
        seekBar: SeekBar,
        durationText: TextView
    ) {
        if (currentMessageId == messageId && mediaPlayer != null) {
            // Same message — toggle play/pause
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.pause()
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                stopProgressUpdates()
            } else {
                mediaPlayer!!.start()
                playPauseButton.setImageResource(android.R.drawable.ic_media_pause)
                startProgressUpdates()
            }
            return
        }

        // Different message or first play — stop current and start new
        stop()

        currentMessageId = messageId
        boundPlayPause = playPauseButton
        boundSeekBar = seekBar
        boundDuration = durationText

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
            }

            seekBar.max = mediaPlayer!!.duration
            playPauseButton.setImageResource(android.R.drawable.ic_media_pause)

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        mediaPlayer?.seekTo(progress)
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })

            mediaPlayer!!.setOnCompletionListener {
                playPauseButton.setImageResource(android.R.drawable.ic_media_play)
                seekBar.progress = 0
                durationText.text = formatDuration(mediaPlayer?.duration ?: 0)
                stopProgressUpdates()
            }

            startProgressUpdates()
        } catch (e: Exception) {
            stop()
        }
    }

    fun stop() {
        stopProgressUpdates()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        currentMessageId = null
        boundPlayPause?.setImageResource(android.R.drawable.ic_media_play)
        boundPlayPause = null
        boundSeekBar = null
        boundDuration = null
    }

    fun isPlaying(messageId: String): Boolean {
        return currentMessageId == messageId && mediaPlayer?.isPlaying == true
    }

    private fun startProgressUpdates() {
        updateRunnable = object : Runnable {
            override fun run() {
                val mp = mediaPlayer ?: return
                if (mp.isPlaying) {
                    boundSeekBar?.progress = mp.currentPosition
                    val remaining = mp.duration - mp.currentPosition
                    boundDuration?.text = formatDuration(remaining)
                    handler.postDelayed(this, 200)
                }
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun stopProgressUpdates() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = null
    }

    private fun formatDuration(millis: Int): String {
        val totalSec = millis / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "$min:${sec.toString().padStart(2, '0')}"
    }
}
