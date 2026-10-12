package com.cubeslauncher.keyboard

import android.graphics.PointF

enum class SwipeResult {
    TAP,
    SWIPE_COMPLETE,
    SWIPE_SINGLE_KEY
}

class SwipeTracker(private val density: Float) {

    val pathPoints = mutableListOf<PointF>()
    val traversedKeys = mutableListOf<Key>()

    var isActive = false
        private set
    var isSwiping = false
        private set

    private var downX = 0f
    private var downY = 0f
    private var totalDistance = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val swipeThresholdPx get() = 10f * density
    private val swipeCompleteMinDistPx get() = 30f * density

    fun onDown(x: Float, y: Float, key: Key) {
        reset()
        isActive = true
        downX = x
        downY = y
        lastX = x
        lastY = y
        pathPoints.add(PointF(x, y))
        traversedKeys.add(key)
    }

    /**
     * Record a move event. Pass null for key if the finger is not over a letter key.
     * Returns true if a new key was added to the traversed list.
     */
    fun onMove(x: Float, y: Float, key: Key?): Boolean {
        if (!isActive) return false

        val dx = x - lastX
        val dy = y - lastY
        val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        totalDistance += dist
        lastX = x
        lastY = y

        pathPoints.add(PointF(x, y))

        if (!isSwiping && totalDistance >= swipeThresholdPx) {
            isSwiping = true
        }

        if (key != null && (traversedKeys.isEmpty() || traversedKeys.last() !== key)) {
            // Deduplicate consecutive same-character keys
            if (traversedKeys.isEmpty() || traversedKeys.last().outputChar != key.outputChar) {
                traversedKeys.add(key)
                return true
            }
        }
        return false
    }

    /**
     * Add a key discovered through path interpolation (between touch events).
     * Does not record a path point or update distance — only affects traversed keys.
     */
    fun addIntermediateKey(key: Key): Boolean {
        if (!isActive) return false
        if (traversedKeys.isEmpty() || traversedKeys.last().outputChar != key.outputChar) {
            traversedKeys.add(key)
            return true
        }
        return false
    }

    fun onUp(): SwipeResult {
        if (!isActive) return SwipeResult.TAP

        return when {
            isSwiping && traversedKeys.size >= 2 && totalDistance >= swipeCompleteMinDistPx ->
                SwipeResult.SWIPE_COMPLETE
            isSwiping -> SwipeResult.SWIPE_SINGLE_KEY
            else -> SwipeResult.TAP
        }
    }

    fun reset() {
        pathPoints.clear()
        traversedKeys.clear()
        isActive = false
        isSwiping = false
        downX = 0f
        downY = 0f
        lastX = 0f
        lastY = 0f
        totalDistance = 0f
    }
}
