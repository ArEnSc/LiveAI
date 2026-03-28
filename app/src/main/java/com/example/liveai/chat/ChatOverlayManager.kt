package com.example.liveai.chat

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

/**
 * Manages the floating chat ball + panel overlay attached to the system WindowManager.
 * The panel reveals from the ball's position, adapting direction based on screen position.
 */
class ChatOverlayManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val llmProvider: com.example.liveai.agent.llm.LlmProvider,
    private val ttsProvider: com.example.liveai.agent.tts.TtsProvider? = null,
    private val systemPrompt: String = ""
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
    private val speechManager = SpeechRecognizerManager(context)
    private val viewModel = ChatOverlayViewModel(speechManager, llmProvider, ttsProvider, systemPrompt)

    private val dp get() = context.resources.displayMetrics.density
    private val screenWidth get() = context.resources.displayMetrics.widthPixels
    private val screenHeight get() = context.resources.displayMetrics.heightPixels

    private var dragController: DragController? = null

    fun create() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun resume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun showTab() {
        if (tabView != null) return

        val shadowPaddingPx = (SHADOW_PADDING_DP * dp).toInt()
        val windowSizePx = (BALL_SIZE_DP * dp).toInt() + shadowPaddingPx * 2

        tabParams = WindowManager.LayoutParams(
            windowSizePx,
            windowSizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = -shadowPaddingPx
            y = screenHeight - windowSizePx - (BOTTOM_MARGIN_DP * dp).toInt()
        }

        tabView = createComposeView {
            ChatTab(isExpanded = isExpanded.value)
        }

        dragController = DragController(
            context = context,
            windowManager = windowManager,
            bounds = DragController.ScreenBounds(
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                windowSize = windowSizePx,
                shadowPadding = shadowPaddingPx
            ),
            physics = ChatHeadSettings.load(context),
            onTap = ::toggle,
            onDrag = { if (isExpanded.value) updatePanelPosition() }
        ).also { it.attachTo(tabView!!) { tabParams } }

        windowManager.addView(tabView, tabParams)
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        updatePanelPosition()

        panelVisible.value = true

        panelView = createComposeView {
            val uiState by viewModel.uiState.collectAsState()
            ChatPanel(
                visible = panelVisible.value,
                messages = uiState.visibleMessages,
                mode = uiState.mode,
                onModeChange = viewModel::onModeChange,
                onSend = viewModel::onSend,
                onCollapseFinished = ::removePanel,
                speechState = uiState.speechState,
                onPressToTalkStart = viewModel::onStartListening,
                onPressToTalkEnd = viewModel::onStopListening
            )
        }
        windowManager.addView(panelView, panelParams)
    }

    private fun hidePanel() {
        viewModel.stopPlayback()
        panelVisible.value = false
    }

    private fun removePanel() {
        removeOverlayView(panelView)
        panelView = null
        panelParams = null
    }

    /**
     * Positions the panel adjacent to the ball. The panel's bottom edge aligns
     * with the ball's bottom edge so the input bar sits at the same level.
     * Panel opens toward screen center horizontally.
     */
    private fun updatePanelPosition() {
        val tParams = tabParams ?: return
        val pParams = panelParams ?: return

        val ballSizePx = (BALL_SIZE_DP * dp).toInt()
        val shadowPad = (SHADOW_PADDING_DP * dp).toInt()
        val panelWidthPx = (PANEL_WIDTH_DP * dp).toInt()
        val panelHeightPx = (PANEL_HEIGHT_DP * dp).toInt()
        val gapPx = (GAP_DP * dp).toInt()

        val ballLeftPx = tParams.x + shadowPad
        val ballTopPx = tParams.y + shadowPad
        val ballCenterX = ballLeftPx + ballSizePx / 2

        val openRight = ballCenterX < screenWidth / 2
        pParams.x = if (openRight) {
            ballLeftPx + ballSizePx + gapPx
        } else {
            ballLeftPx - panelWidthPx - gapPx
        }

        val inputGapPx = (8 * dp).toInt()
        pParams.y = (ballTopPx - panelHeightPx - inputGapPx).coerceAtLeast(0)

        if (panelView != null) {
            try {
                windowManager.updateViewLayout(panelView, pParams)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update panel position", e)
            }
        }
    }

    fun destroy() {
        dragController?.destroy()
        dragController = null
        speechManager.destroy()
        viewModel.destroy()

        removeOverlayView(panelView)
        panelView = null
        panelParams = null

        removeOverlayView(tabView)
        tabView = null
        tabParams = null

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    /**
     * Disposes the Compose composition and removes the view from WindowManager.
     * Both steps are guarded independently so a failure in one doesn't prevent the other.
     */
    private fun removeOverlayView(view: ComposeView?) {
        if (view == null) return
        try {
            view.disposeComposition()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispose composition", e)
        }
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove view", e)
        }
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
        const val SHADOW_PADDING_DP = 12
        const val BOTTOM_MARGIN_DP = 120
        const val PANEL_WIDTH_DP = 280
        const val PANEL_HEIGHT_DP = 360
        const val GAP_DP = 8
    }
}
