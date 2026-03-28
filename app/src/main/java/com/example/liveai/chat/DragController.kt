package com.example.liveai.chat

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs

/**
 * Handles drag-to-move on a WindowManager-attached view with chat-head physics.
 * On release: spring-snaps to nearest screen edge (X), flings with momentum (Y).
 */
class DragController(
    context: Context,
    private val windowManager: WindowManager,
    private val bounds: ScreenBounds,
    private val physics: ChatHeadSettings.Physics,
    private val onTap: () -> Unit,
    private val onDrag: () -> Unit = {},
    private val onSettled: () -> Unit = {}
) {

    data class ScreenBounds(
        val screenWidth: Int,
        val screenHeight: Int,
        val windowSize: Int,
        val shadowPadding: Int
    )

    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private var velocityTracker: VelocityTracker? = null
    private var springAnimX: SpringAnimation? = null
    private var flingAnimY: FlingAnimation? = null
    private var springAnimY: SpringAnimation? = null

    fun attachTo(view: View, params: () -> WindowManager.LayoutParams?) {
        view.setOnTouchListener { _, event ->
            val lp = params() ?: return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelAnimations()
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false

                    releaseVelocityTracker()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)

                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        lp.x = initialX + dx.toInt()
                        lp.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(view, lp)
                        onDrag()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        releaseVelocityTracker()
                        onTap()
                    } else if (physics.snapToEdge) {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        val vy = velocityTracker?.yVelocity ?: 0f
                        releaseVelocityTracker()
                        animateToEdge(view, lp, vx, vy)
                    } else {
                        releaseVelocityTracker()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    releaseVelocityTracker()
                    true
                }
                else -> false
            }
        }
    }

    private fun animateToEdge(
        view: View,
        lp: WindowManager.LayoutParams,
        velocityX: Float,
        velocityY: Float
    ) {
        val currentX = lp.x.toFloat()
        val currentY = lp.y.toFloat()

        val ballCenterX = currentX + bounds.shadowPadding +
            (bounds.windowSize - bounds.shadowPadding * 2) / 2f
        val screenCenter = bounds.screenWidth / 2f
        val velocityBias = velocityX * VELOCITY_BIAS_FACTOR
        val snapLeft = (ballCenterX + velocityBias) < screenCenter

        val targetX = if (snapLeft) {
            -bounds.shadowPadding.toFloat()
        } else {
            (bounds.screenWidth - bounds.windowSize + bounds.shadowPadding).toFloat()
        }

        val minY = 0f
        val maxY = (bounds.screenHeight - bounds.windowSize).toFloat()

        // Spring animation for X (snap to edge with bounce)
        val xHolder = FloatValueHolder(currentX)
        springAnimX = SpringAnimation(xHolder).apply {
            spring = SpringForce(targetX).apply {
                stiffness = physics.springStiffness
                dampingRatio = physics.springDamping
            }
            addUpdateListener { _, value, _ ->
                lp.x = value.toInt()
                tryUpdateLayout(view, lp)
                onDrag()
            }
            addEndListener { _, _, _, _ ->
                onSettled()
            }
            start()
        }

        // Y axis: fling with momentum, clamped to screen bounds
        val yHolder = FloatValueHolder(currentY)
        flingAnimY = FlingAnimation(yHolder).apply {
            setStartVelocity(velocityY)
            friction = physics.flingFriction
            setMinValue(minY)
            setMaxValue(maxY)
            addUpdateListener { _, value, _ ->
                lp.y = value.toInt()
                tryUpdateLayout(view, lp)
                onDrag()
            }
            addEndListener { _, _, finalValue, _ ->
                val clamped = finalValue.coerceIn(minY, maxY)
                if (abs(finalValue - clamped) > 1f) {
                    springSettleY(view, lp, yHolder, clamped)
                }
            }
            start()
        }
    }

    private fun springSettleY(
        view: View,
        lp: WindowManager.LayoutParams,
        holder: FloatValueHolder,
        target: Float
    ) {
        springAnimY = SpringAnimation(holder).apply {
            spring = SpringForce(target).apply {
                stiffness = SpringForce.STIFFNESS_MEDIUM
                dampingRatio = SpringForce.DAMPING_RATIO_NO_BOUNCY
            }
            addUpdateListener { _, value, _ ->
                lp.y = value.toInt()
                tryUpdateLayout(view, lp)
            }
            start()
        }
    }

    private fun tryUpdateLayout(view: View, lp: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: IllegalArgumentException) {
            cancelAnimations()
        }
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun cancelAnimations() {
        springAnimX?.cancel()
        springAnimX = null
        flingAnimY?.cancel()
        flingAnimY = null
        springAnimY?.cancel()
        springAnimY = null
    }

    fun destroy() {
        cancelAnimations()
        releaseVelocityTracker()
    }

    companion object {
        private const val VELOCITY_BIAS_FACTOR = 0.15f
    }
}
