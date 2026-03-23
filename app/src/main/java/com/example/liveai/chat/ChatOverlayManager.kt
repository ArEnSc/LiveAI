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
 * Manages the floating chat ball + panel overlay attached to the system WindowManager.
 * The panel reveals from the ball's position, adapting direction based on screen position.
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

    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private val isExpanded = mutableStateOf(false)
    private val panelVisible = mutableStateOf(false)

    private val dp get() = context.resources.displayMetrics.density
    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels

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
                        // Move panel with the ball if expanded
                        if (isExpanded.value) {
                            updatePanelPosition()
                        }
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
        val expanding = !isExpanded.value
        isExpanded.value = expanding
        Log.d(TAG, "Chat overlay toggled: expanded=$expanding")

        if (expanding) {
            showPanel()
        } else {
            hidePanel()
        }
    }

    private fun showPanel() {
        if (panelView != null) return

        val panelWidthPx = (PANEL_WIDTH_DP * dp).toInt()
        val panelHeightPx = (PANEL_HEIGHT_DP * dp).toInt()

        panelParams = WindowManager.LayoutParams(
            panelWidthPx,
            panelHeightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        updatePanelPosition()

        // Panel composable reads panelVisible; starts false, auto-expands via LaunchedEffect
        panelVisible.value = true

        panelView = createComposeView {
            ChatPanel(
                visible = panelVisible.value,
                onCollapseFinished = ::removePanel
            )
        }
        windowManager.addView(panelView, panelParams)
    }

    private fun hidePanel() {
        // Flip to false — ChatPanel's LaunchedEffect animates scaleX to 0,
        // then calls onCollapseFinished which removes the window.
        panelVisible.value = false
    }

    private fun removePanel() {
        panelView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove panel", e)
            }
        }
        panelView = null
        panelParams = null
    }

    /**
     * Positions the panel adjacent to the ball based on which side of the screen
     * the ball is on. Panel opens toward screen center.
     */
    private fun updatePanelPosition() {
        val tParams = tabParams ?: return
        val pParams = panelParams ?: return

        val ballSizePx = (BALL_SIZE_DP * dp).toInt()
        val panelWidthPx = (PANEL_WIDTH_DP * dp).toInt()
        val panelHeightPx = (PANEL_HEIGHT_DP * dp).toInt()
        val gapPx = (GAP_DP * dp).toInt()

        val ballCenterX = tParams.x + ballSizePx / 2
        val ballCenterY = tParams.y + ballSizePx / 2

        // Horizontal: panel to the right if ball is on left half, otherwise left
        val openRight = ballCenterX < screenWidth / 2
        pParams.x = if (openRight) {
            tParams.x + ballSizePx + gapPx
        } else {
            tParams.x - panelWidthPx - gapPx
        }

        // Vertical: anchor panel so its vertical center aligns with ball center,
        // but clamp to screen bounds
        pParams.y = (ballCenterY - panelHeightPx / 2)
            .coerceIn(0, screenHeight - panelHeightPx)

        if (panelView != null) {
            try {
                windowManager.updateViewLayout(panelView, pParams)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update panel position", e)
            }
        }
    }

    fun destroy() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        panelView = null
        panelParams = null

        tabView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
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
        const val PANEL_WIDTH_DP = 280
        const val PANEL_HEIGHT_DP = 360
        const val GAP_DP = 8
    }
}
