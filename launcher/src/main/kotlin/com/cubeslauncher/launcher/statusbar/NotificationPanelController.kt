package com.cubeslauncher.launcher.statusbar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.launcher.R
import com.cubeslauncher.launcher.databinding.ViewNotificationPanelBinding
import com.cubeslauncher.launcher.model.NotificationInfo
import kotlin.math.abs

/**
 * Manages the notification/quick-settings panel with WP10-style drag gestures.
 *
 * The panel lives off-screen (translationY = -panelHeight) when closed and at
 * translationY = 0 when fully open. During a drag the panel follows the finger
 * in real time; on release it either snaps open or closed based on position and
 * fling velocity — exactly like the Windows Phone 10 Action Center.
 */
class NotificationPanelController(
    private val context: Context,
    private val binding: ViewNotificationPanelBinding,
    private val quickSettingsController: QuickSettingsController
) {

    // --- public state -----------------------------------------------------------

    var isOpen = false
        private set

    /** True while the user is actively dragging the panel. */
    var isDragging = false
        private set

    // --- constants --------------------------------------------------------------

    companion object {
        /** Minimum px the finger must move before we claim the gesture. */
        private const val DRAG_SLOP = 24f
        /** Fling velocity (px/s) above which we snap regardless of position. */
        private const val FLING_VELOCITY = 800f
        /** Duration of the settle animation (ms). */
        private const val SETTLE_DURATION = 280L
    }

    // --- internal drag state ----------------------------------------------------

    private var dragStartY = 0f
    private var dragStartTranslation = 0f
    private var dragConfirmed = false
    private var velocityTracker: VelocityTracker? = null
    private var panelHeight = 0

    /** Current settle animator so we can cancel it if a new drag starts. */
    private var settleAnimator: ValueAnimator? = null

    // --- adapter ----------------------------------------------------------------

    private val adapter = NotificationsAdapter(
        onDismiss = { NotificationService.instance?.dismissNotification(it.key) },
        onTap = { openApp(it) }
    )

    // --- setup ------------------------------------------------------------------

    fun setup() {
        binding.notificationsRecycler.layoutManager = LinearLayoutManager(context)
        binding.notificationsRecycler.adapter = adapter

        binding.clearAllButton.setOnClickListener {
            NotificationService.instance?.dismissAll()
        }

        binding.allSettingsButton.setOnClickListener {
            context.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            animateClose()
        }

        setupExpandCollapse()
        quickSettingsController.setup(binding)

        // Make sure we know the height once laid out.
        binding.notificationPanel.post {
            panelHeight = binding.notificationPanel.height
        }
    }

    private fun setupExpandCollapse() {
        binding.expandCollapseButton.setOnClickListener {
            if (binding.quickSettingsRow2.visibility == View.GONE) {
                binding.quickSettingsRow2.visibility = View.VISIBLE
                binding.expandCollapseIcon.setImageResource(R.drawable.ic_qs_collapse)
                binding.expandCollapseText.setText(R.string.collapse)
            } else {
                binding.quickSettingsRow2.visibility = View.GONE
                binding.expandCollapseIcon.setImageResource(R.drawable.ic_qs_expand)
                binding.expandCollapseText.setText(R.string.expand)
            }
        }
    }

    // --- notification data ------------------------------------------------------

    fun updateNotifications(notifications: List<NotificationInfo>) {
        adapter.submitList(notifications)
        binding.emptyText.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        binding.clearAllButton.visibility =
            if (notifications.isEmpty()) View.GONE else View.VISIBLE
    }

    // --- tap toggle (status bar click) ------------------------------------------

    fun toggle() {
        if (isOpen) animateClose() else animateOpen()
    }

    // --- touch handling ---------------------------------------------------------

    /**
     * Called from Activity.dispatchTouchEvent(). Returns true when this
     * controller is consuming the event (dragging the panel).
     */
    fun onTouchEvent(ev: MotionEvent): Boolean {
        if (panelHeight == 0) {
            // Not laid out yet — measure now.
            binding.notificationPanel.visibility = View.VISIBLE
            binding.notificationPanel.translationY =
                -binding.notificationPanel.height.toFloat()
            panelHeight = binding.notificationPanel.height
            if (panelHeight == 0) return false
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                settleAnimator?.cancel()
                dragStartY = ev.rawY
                dragStartTranslation = binding.notificationPanel.translationY
                dragConfirmed = false
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(ev)
                return false // let the down pass through initially
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(ev)
                val dy = ev.rawY - dragStartY

                if (!dragConfirmed) {
                    // Require vertical movement > slop before claiming
                    if (abs(dy) > DRAG_SLOP) {
                        // Opening: only allow downward from top zone when closed
                        // Closing: only allow upward when open
                        val validOpen = !isOpen && dy > 0
                        val validClose = isOpen && dy < 0
                        if (validOpen || validClose) {
                            dragConfirmed = true
                            isDragging = true
                            if (!isOpen) {
                                // Make visible at fully-hidden position
                                binding.notificationPanel.visibility = View.VISIBLE
                                binding.notificationPanel.translationY = -panelHeight.toFloat()
                                dragStartTranslation = -panelHeight.toFloat()
                                quickSettingsController.refreshStates()
                            }
                            dragStartY = ev.rawY // reset so the panel doesn't jump
                        } else {
                            return false
                        }
                    } else {
                        return false
                    }
                }

                // Track: clamp translation between -panelHeight and 0
                val newTranslation = (dragStartTranslation + (ev.rawY - dragStartY))
                    .coerceIn(-panelHeight.toFloat(), 0f)
                binding.notificationPanel.translationY = newTranslation
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragConfirmed) {
                    isDragging = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return false
                }

                velocityTracker?.addMovement(ev)
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityY = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null
                isDragging = false
                dragConfirmed = false

                settle(velocityY)
                return true
            }
        }
        return isDragging
    }

    // --- settle animation -------------------------------------------------------

    /**
     * After the finger lifts, decide whether to snap open or closed based on
     * current position and fling velocity, then animate to the target.
     */
    private fun settle(velocityY: Float) {
        val currentY = binding.notificationPanel.translationY
        val progress = 1f + (currentY / panelHeight) // 0 = hidden, 1 = open

        val shouldOpen = when {
            velocityY > FLING_VELOCITY -> true    // fling down → open
            velocityY < -FLING_VELOCITY -> false   // fling up → close
            else -> progress > 0.5f                // past halfway → open
        }

        val targetY = if (shouldOpen) 0f else -panelHeight.toFloat()

        // Duration proportional to remaining distance
        val fraction = abs(targetY - currentY) / panelHeight
        val duration = (SETTLE_DURATION * fraction).toLong().coerceIn(80, SETTLE_DURATION)

        settleAnimator?.cancel()
        settleAnimator = ValueAnimator.ofFloat(currentY, targetY).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                binding.notificationPanel.translationY = it.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (shouldOpen) {
                        isOpen = true
                    } else {
                        isOpen = false
                        binding.notificationPanel.visibility = View.GONE
                    }
                }
            })
            start()
        }
    }

    // --- programmatic open / close (for tap & back press) -----------------------

    fun animateOpen() {
        if (isOpen) return
        settleAnimator?.cancel()
        quickSettingsController.refreshStates()
        binding.notificationPanel.visibility = View.VISIBLE
        panelHeight = binding.notificationPanel.height
        binding.notificationPanel.translationY = -panelHeight.toFloat()

        settleAnimator = ValueAnimator.ofFloat(-panelHeight.toFloat(), 0f).apply {
            duration = SETTLE_DURATION
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                binding.notificationPanel.translationY = it.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isOpen = true
                }
            })
            start()
        }
    }

    fun animateClose() {
        if (!isOpen && !isDragging) return
        settleAnimator?.cancel()
        panelHeight = binding.notificationPanel.height

        settleAnimator = ValueAnimator.ofFloat(
            binding.notificationPanel.translationY,
            -panelHeight.toFloat()
        ).apply {
            duration = SETTLE_DURATION
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                binding.notificationPanel.translationY = it.animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.notificationPanel.visibility = View.GONE
                    isOpen = false
                }
            })
            start()
        }
    }

    /** Alias kept for back-press handler. */
    fun close() = animateClose()
    fun open() = animateOpen()

    // --- helpers ----------------------------------------------------------------

    private fun openApp(notification: NotificationInfo) {
        val intent = context.packageManager.getLaunchIntentForPackage(notification.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            animateClose()
        }
    }
}
