package com.example.liveai.interaction

import android.content.Context
import android.graphics.RectF
import android.util.Log
import android.view.MotionEvent
import com.example.liveai.live2d.LAppLive2DManager

/**
 * Routes touch pointers to body-part interactions.
 *
 * Hit zones are loaded from [HitZoneConfig] (user-configured via the setup
 * activity). They are stored as normalized screen ratios (0..1) and converted
 * to pixel coordinates using the current screen dimensions.
 *
 * Manages per-pointer state, computes angles via InteractionEngine,
 * applies them to the Live2D model, and runs spring-back on release.
 */
class TouchInteractionHandler(
    private val manager: LAppLive2DManager,
    context: Context
) {
    companion object {
        private const val TAG = "TouchInteraction"
    }

    // Normalized hit zones (0..1 of screen)
    private val headZoneNorm: RectF = HitZoneConfig.loadHeadZone(context)
    private val bodyZoneNorm: RectF = HitZoneConfig.loadBodyZone(context)

    private val activePointers = mutableMapOf<Int, PointerInteraction>()
    private var activeSpring: ActiveSpring? = null

    private data class ActiveSpring(
        val bodyPart: BodyPart,
        val animator: SpringAnimator
    )

    /**
     * Process a MotionEvent. Returns true if the event was consumed
     * by an interaction (hit a body part zone).
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val pointerId = event.getPointerId(idx)
                val x = event.getX(idx)
                val y = event.getY(idx)

                val bodyPart = hitTest(x, y) ?: return false

                // Cancel any running spring for this body part
                if (activeSpring?.bodyPart == bodyPart) {
                    activeSpring = null
                }

                activePointers[pointerId] = PointerInteraction(
                    bodyPart = bodyPart,
                    startX = x,
                    startY = y,
                    lastX = x,
                    lastY = y
                )
                Log.d(TAG, "Drag start: $bodyPart pointer=$pointerId")
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointers.isEmpty()) return false

                for ((pointerId, state) in activePointers) {
                    val pointerIndex = event.findPointerIndex(pointerId)
                    if (pointerIndex < 0) continue

                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    val deltaX = x - state.startX
                    val deltaY = y - state.startY

                    val angles = InteractionEngine.computeAngles(state.bodyPart, deltaX, deltaY)
                    state.currentAngles = angles
                    state.lastX = x
                    state.lastY = y

                    applyAngles(state.bodyPart, angles)
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                val state = activePointers.remove(pointerId) ?: return false

                Log.d(TAG, "Drag end: ${state.bodyPart} angles=${state.currentAngles}")
                startSpringBack(state.bodyPart, state.currentAngles)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                for ((_, state) in activePointers) {
                    startSpringBack(state.bodyPart, state.currentAngles)
                }
                activePointers.clear()
                return true
            }
        }
        return false
    }

    /**
     * Must be called each frame to advance spring-back animations.
     * Call this from the draw loop.
     */
    fun updateSpring() {
        val spring = activeSpring ?: return

        // Don't animate spring if user is actively dragging that body part
        val activeDragOnSamePart = activePointers.values.any { it.bodyPart == spring.bodyPart }
        if (activeDragOnSamePart) return

        val angles = spring.animator.update()
        applyAngles(spring.bodyPart, angles)

        if (spring.animator.isFinished) {
            clearAngles(spring.bodyPart)
            activeSpring = null
        }
    }

    private fun applyAngles(bodyPart: BodyPart, angles: InteractionAngles) {
        when (bodyPart) {
            BodyPart.HEAD -> manager.setHeadInteractionAngles(angles.angleX, angles.angleY)
            BodyPart.BODY -> manager.setBodyInteractionAngles(angles.angleX, angles.angleY, angles.angleZ)
        }
    }

    private fun clearAngles(bodyPart: BodyPart) {
        when (bodyPart) {
            BodyPart.HEAD -> manager.clearHeadInteraction()
            BodyPart.BODY -> manager.clearBodyInteraction()
        }
    }

    private fun startSpringBack(bodyPart: BodyPart, fromAngles: InteractionAngles) {
        activeSpring = ActiveSpring(
            bodyPart = bodyPart,
            animator = SpringAnimator.forBodyPart(bodyPart, fromAngles)
        )
    }

    /**
     * Hit-test a screen coordinate against saved body-part zones.
     * Zones are normalized (0..1) and scaled to current screen dimensions.
     */
    private fun hitTest(screenX: Float, screenY: Float): BodyPart? {
        val w = manager.windowWidth.toFloat()
        val h = manager.windowHeight.toFloat()
        if (w <= 0f || h <= 0f) return null

        val headPixels = HitZoneConfig.toPixels(headZoneNorm, w, h)
        val bodyPixels = HitZoneConfig.toPixels(bodyZoneNorm, w, h)

        // Head takes priority over body in overlapping regions
        if (headPixels.contains(screenX, screenY)) return BodyPart.HEAD
        if (bodyPixels.contains(screenX, screenY)) return BodyPart.BODY

        return null
    }
}
