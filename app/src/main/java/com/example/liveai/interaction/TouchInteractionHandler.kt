package com.example.liveai.interaction

import android.util.Log
import android.view.MotionEvent

/**
 * Routes touch pointers to interaction zones.
 *
 * Decoupled from Live2D — communicates through [InteractionTarget].
 * Zones are data-driven: each zone defines its own hit rectangle,
 * parameter bindings, and spring config.
 */
class TouchInteractionHandler(
    private val target: InteractionTarget,
    private val zones: List<InteractionZone>
) {
    companion object {
        private const val TAG = "TouchInteraction"
    }

    private val activePointers = mutableMapOf<Int, PointerInteraction>()
    private var activeSpring: ActiveSpring? = null

    private data class ActiveSpring(
        val zone: InteractionZone,
        val animator: SpringAnimator
    )

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                return handlePointerDown(event)
            MotionEvent.ACTION_MOVE ->
                return handlePointerMove(event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                return handlePointerUp(event)
            MotionEvent.ACTION_CANCEL -> {
                for ((_, state) in activePointers) {
                    if (state.zone.holdParams.isNotEmpty()) {
                        target.clearInteractionParams(state.zone.holdParams.keys)
                    }
                    val springValues = state.currentValues - state.zone.holdParams.keys
                    startSpringBack(state.zone, springValues)
                }
                activePointers.clear()
                return false
            }
        }
        return false
    }

    /** Advance spring-back animations. Call each frame from the draw loop. */
    fun updateSpring() {
        val spring = activeSpring ?: return

        val activeDragOnSameZone = activePointers.values.any { it.zone.id == spring.zone.id }
        if (activeDragOnSameZone) return

        val values = spring.animator.update()
        target.setInteractionParams(values)

        if (spring.animator.isFinished) {
            target.clearInteractionParams(values.keys)
            activeSpring = null
        }
    }

    private fun handlePointerDown(event: MotionEvent): Boolean {
        val idx = event.actionIndex
        val pointerId = event.getPointerId(idx)
        val x = event.getX(idx)
        val y = event.getY(idx)

        val zone = hitTest(x, y) ?: return false

        // Cancel running spring for this zone
        if (activeSpring?.zone?.id == zone.id) {
            activeSpring = null
        }

        activePointers[pointerId] = PointerInteraction(
            zone = zone,
            startX = x, startY = y,
            lastX = x, lastY = y,
            currentValues = zone.holdParams
        )
        if (zone.holdParams.isNotEmpty()) {
            target.setInteractionParams(zone.holdParams)
        }
        Log.d(TAG, "Drag start: ${zone.name} pointer=$pointerId")
        return true
    }

    private fun handlePointerMove(event: MotionEvent): Boolean {
        if (activePointers.isEmpty()) return false

        for ((pointerId, state) in activePointers) {
            val pointerIndex = event.findPointerIndex(pointerId)
            if (pointerIndex < 0) continue

            val x = event.getX(pointerIndex)
            val y = event.getY(pointerIndex)
            val bindingValues = InteractionEngine.computeBindingValues(
                state.zone.bindings,
                x - state.startX,
                y - state.startY,
                state.zone.sensitivity
            )
            val values = state.zone.holdParams + bindingValues
            state.currentValues = values
            state.lastX = x
            state.lastY = y

            target.setInteractionParams(values)
        }
        return true
    }

    private fun handlePointerUp(event: MotionEvent): Boolean {
        val pointerId = event.getPointerId(event.actionIndex)
        val state = activePointers.remove(pointerId) ?: return false

        Log.d(TAG, "Drag end: ${state.zone.name}")
        // Clear holdParams immediately (they snap back to resting state),
        // spring-back only animates the drag-proportional binding values.
        if (state.zone.holdParams.isNotEmpty()) {
            target.clearInteractionParams(state.zone.holdParams.keys)
        }
        val springValues = state.currentValues - state.zone.holdParams.keys
        startSpringBack(state.zone, springValues)
        return true
    }

    private fun startSpringBack(zone: InteractionZone, fromValues: Map<String, Float>) {
        activeSpring = ActiveSpring(
            zone = zone,
            animator = SpringAnimator(fromValues, zone.spring)
        )
    }

    private fun hitTest(screenX: Float, screenY: Float): InteractionZone? {
        val w = target.getScreenWidth().toFloat()
        val h = target.getScreenHeight().toFloat()
        if (w <= 0f || h <= 0f) return null

        val scale = target.getModelScale()
        val offX = target.getModelOffsetX()
        val offY = target.getModelOffsetY()

        // First zone in the list that contains the point wins (user controls priority via ordering)
        for (zone in zones) {
            // Transform zone from model space to screen space, then to pixels
            val screenRect = ZoneTransform.modelToScreen(zone.rect, scale, offX, offY)
            val px = screenRect.left * w
            val py = screenRect.top * h
            val pr = screenRect.right * w
            val pb = screenRect.bottom * h
            if (screenX in px..pr && screenY in py..pb) {
                return zone
            }
        }
        return null
    }
}
