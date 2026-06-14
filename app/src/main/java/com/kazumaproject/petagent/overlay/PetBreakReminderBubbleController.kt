package com.kazumaproject.petagent.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import com.kazumaproject.petagent.breakreminder.BreakReminderTone
import kotlin.math.roundToInt

class PetBreakReminderBubbleController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onAcceptClicked: () -> Unit,
    private val onSnoozeClicked: () -> Unit,
    private val onDismissTodayClicked: () -> Unit,
) {
    private var bubbleView: PetBreakReminderBubbleView? = null

    fun showNearPet(
        petBounds: Rect,
        message: String,
        activeMinutes: Int,
        tone: BreakReminderTone,
    ) {
        removeAll()
        val view = PetBreakReminderBubbleView(
            context = context,
            message = message,
            activeMinutes = activeMinutes,
            tone = tone,
            onAcceptClicked = {
                removeAll()
                onAcceptClicked()
            },
            onSnoozeClicked = {
                removeAll()
                onSnoozeClicked()
            },
            onDismissTodayClicked = {
                removeAll()
                onDismissTodayClicked()
            },
        )
        val params = bubbleLayoutParams(petBounds)
        bubbleView = view
        windowManager.addView(view, params)
    }

    fun removeAll() {
        val view = bubbleView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The overlay may already be gone while the service is stopping.
        }
        bubbleView = null
    }

    private fun bubbleLayoutParams(petBounds: Rect): WindowManager.LayoutParams {
        val metrics = context.resources.displayMetrics
        val width = dp(286)
        val x = (petBounds.centerX() - width / 2)
            .coerceIn(dp(8), (metrics.widthPixels - width - dp(8)).coerceAtLeast(dp(8)))
        val y = (petBounds.top - dp(152)).let {
            if (it >= dp(12)) it else (petBounds.bottom + dp(8))
        }.coerceIn(dp(8), (metrics.heightPixels - dp(190)).coerceAtLeast(dp(8)))
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
