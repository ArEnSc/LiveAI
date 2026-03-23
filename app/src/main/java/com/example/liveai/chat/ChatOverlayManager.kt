package com.example.liveai.chat

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.abs

/**
 * Manages the floating chat ball overlay attached to the system WindowManager.
 * Handles drag-to-move and tap-to-toggle via raw touch events on the window.
 */
class ChatOverlayManager(
    private val context: Context,
    private val windowManager: WindowManager
) : LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var tabView: ComposeView? = null
    private var tabParams: WindowManager.LayoutParams? = null

    private val isExpanded = mutableStateOf(false)

    // Drag tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    fun create() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun showTab() {
        if (tabView != null) return

        val dp = context.resources.displayMetrics.density
        val screenHeight = context.resources.displayMetrics.heightPixels
        val sizePx = (BALL_SIZE_DP * dp).toInt()

        tabParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = screenHeight - sizePx - (BOTTOM_MARGIN_DP * dp).toInt()
        }

        tabView = createComposeView {
            ChatTab(isExpanded = isExpanded.value)
        }

        setupTouchHandling(tabView!!)
        windowManager.addView(tabView, tabParams)
    }

    private fun setupTouchHandling(view: ComposeView) {
        val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop

        view.setOnTouchListener { _, event ->
            val params = tabParams ?: return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggle()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggle() {
        isExpanded.value = !isExpanded.value
        Log.d(TAG, "Chat overlay toggled: expanded=${isExpanded.value}")
    }

    fun destroy() {
        tabView?.let { windowManager.removeView(it) }
        tabView = null
        tabParams = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    private fun createComposeView(content: @Composable () -> Unit): ComposeView {
        return ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@ChatOverlayManager)
            setViewTreeSavedStateRegistryOwner(this@ChatOverlayManager)
            setContent { content() }
        }
    }

    companion object {
        private const val TAG = "LiveAI"
        const val BALL_SIZE_DP = 52
        const val BOTTOM_MARGIN_DP = 120
    }
}
