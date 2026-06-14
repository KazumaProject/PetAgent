package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import com.kazumaproject.petagent.breakreminder.BreakStatus
import kotlin.math.roundToInt

class PetBreakStatusPanelController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onBreakNowClicked: () -> Unit,
    private val onSnoozeClicked: () -> Unit,
    private val onSettingsClicked: () -> Unit,
) {
    private var panelView: PetBreakStatusPanelView? = null

    fun showNearPet(petBounds: Rect, status: BreakStatus) {
        removeAll()
        val view = PetBreakStatusPanelView(
            context = context,
            status = status,
            onBreakNowClicked = {
                removeAll()
                onBreakNowClicked()
            },
            onSnoozeClicked = {
                removeAll()
                onSnoozeClicked()
            },
            onSettingsClicked = {
                removeAll()
                onSettingsClicked()
            },
        )
        val params = panelLayoutParams(petBounds)
        panelView = view
        windowManager.addView(view, params)
    }

    fun removeAll() {
        val view = panelView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The overlay may already be gone while the service is stopping.
        }
        panelView = null
    }

    private fun panelLayoutParams(petBounds: Rect): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        val width = dp(282)
        val x = (petBounds.centerX() - width / 2)
            .coerceIn(dp(8), (metrics.widthPixels - width - dp(8)).coerceAtLeast(dp(8)))
        val preferredY = petBounds.bottom + dp(8)
        val y = preferredY.coerceIn(dp(8), (metrics.heightPixels - dp(240)).coerceAtLeast(dp(8)))
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
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
