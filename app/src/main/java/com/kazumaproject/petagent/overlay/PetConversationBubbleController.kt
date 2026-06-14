package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.kazumaproject.petagent.llm.LocalModelMetadata
import com.kazumaproject.petagent.llm.LocalModelState
import kotlin.math.roundToInt

class PetConversationBubbleController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onSendMessage: (String) -> Unit,
    private val onDownloadWifiClicked: () -> Unit,
    private val onDownloadMobileClicked: () -> Unit,
) {
    private var bubbleView: PetConversationBubbleView? = null
    private var currentBounds: Rect? = null
    private var currentFocusable = false

    fun showModelSetup(
        petBounds: Rect,
        metadata: LocalModelMetadata,
        state: LocalModelState,
    ) {
        val view = ensureView(petBounds, focusable = false)
        view.showModelSetup(
            metadata = metadata,
            state = state,
            onDownloadWifiClicked = onDownloadWifiClicked,
            onDownloadMobileClicked = onDownloadMobileClicked,
            onCloseClicked = { removeAll() },
        )
    }

    fun showInput(petBounds: Rect) {
        val view = ensureView(petBounds, focusable = true)
        view.showInput(
            onSendClicked = { message ->
                hideKeyboard()
                onSendMessage(message)
            },
            onCloseClicked = { removeAll() },
        )
        view.post {
            view.focusInput()
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun showResponse(
        petBounds: Rect,
        text: String,
        isWorking: Boolean,
        isError: Boolean = false,
    ) {
        val view = ensureView(petBounds, focusable = false)
        view.showResponse(
            text = text,
            isWorking = isWorking,
            isError = isError,
            onNewMessageClicked = { showInput(petBounds) },
            onCloseClicked = { removeAll() },
        )
    }

    fun removeAll() {
        hideKeyboard()
        val view = bubbleView
        if (view != null) {
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // The overlay may already be gone while the service is stopping.
            }
        }
        bubbleView = null
        currentBounds = null
        currentFocusable = false
    }

    private fun ensureView(petBounds: Rect, focusable: Boolean): PetConversationBubbleView {
        val existing = bubbleView
        val shouldRecreate = existing == null || focusable != currentFocusable
        if (shouldRecreate) {
            removeAll()
            val view = PetConversationBubbleView(context)
            bubbleView = view
            currentBounds = petBounds
            currentFocusable = focusable
            windowManager.addView(view, bubbleLayoutParams(petBounds, focusable))
            return view
        }

        if (currentBounds != petBounds) {
            currentBounds = petBounds
            try {
                windowManager.updateViewLayout(existing, bubbleLayoutParams(petBounds, focusable))
            } catch (_: IllegalArgumentException) {
                // The window can disappear during service teardown.
            }
        }
        return existing
    }

    private fun bubbleLayoutParams(
        petBounds: Rect,
        focusable: Boolean,
    ): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        val width = dp(304)
        val x = (petBounds.centerX() - width / 2)
            .coerceIn(dp(8), (metrics.widthPixels - width - dp(8)).coerceAtLeast(dp(8)))
        val y = (petBounds.top - dp(180)).let {
            if (it >= dp(12)) it else (petBounds.bottom + dp(8))
        }.coerceIn(dp(8), (metrics.heightPixels - dp(260)).coerceAtLeast(dp(8)))
        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    private fun hideKeyboard() {
        val view = bubbleView ?: return
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).roundToInt()
    }
}
