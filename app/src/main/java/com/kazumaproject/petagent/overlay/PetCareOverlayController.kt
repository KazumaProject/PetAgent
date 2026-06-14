package com.kazumaproject.petagent.overlay

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.kazumaproject.petagent.game.PetCareUiState
import com.kazumaproject.petagent.game.PrimaryNeed
import kotlin.math.roundToInt

class PetCareOverlayController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onFoodClicked: () -> Unit,
    private val onWaterClicked: () -> Unit,
    private val onPlayClicked: () -> Unit,
    private val onNeedClicked: (PrimaryNeed) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var careMenuView: CareMenuView? = null
    private var careMenuParams: WindowManager.LayoutParams? = null
    private var needBubbleView: NeedBubbleView? = null
    private var needBubbleParams: WindowManager.LayoutParams? = null
    private val activeFlyViews = mutableSetOf<View>()
    private val activeFlyAnimators = mutableSetOf<ValueAnimator>()

    private val hideMenuRunnable = Runnable { hideCareMenu() }

    fun showCareMenuNearPet(petBounds: Rect) {
        val view = careMenuView ?: CareMenuView(
            context = context,
            onFoodClicked = onFoodClicked,
            onWaterClicked = onWaterClicked,
            onPlayClicked = onPlayClicked,
        ).also {
            careMenuView = it
        }

        val measuredSize = measure(view)
        val params = careMenuParams ?: baseLayoutParams().also {
            careMenuParams = it
        }
        val point = menuPoint(petBounds, measuredSize.width, measuredSize.height)
        params.x = point.x
        params.y = point.y

        if (view.parent == null) {
            windowManager.addView(view, params)
        } else {
            updateViewLayout(view, params)
        }

        handler.removeCallbacks(hideMenuRunnable)
        handler.postDelayed(hideMenuRunnable, MENU_AUTO_HIDE_MS)
    }

    fun hideCareMenu() {
        handler.removeCallbacks(hideMenuRunnable)
        val view = careMenuView ?: return
        removeView(view)
        careMenuParams = null
    }

    fun updateNeedBubble(petBounds: Rect, careUiState: PetCareUiState) {
        val primaryNeed = careUiState.primaryNeed
        if (primaryNeed == null) {
            hideNeedBubble()
            return
        }

        val view = needBubbleView ?: NeedBubbleView(context, primaryNeed, onNeedClicked).also {
            needBubbleView = it
        }
        view.updateNeed(primaryNeed)

        val measuredSize = measure(view)
        val params = needBubbleParams ?: baseLayoutParams().also {
            needBubbleParams = it
        }
        val point = bubblePoint(petBounds, measuredSize.width, measuredSize.height)
        params.x = point.x
        params.y = point.y

        if (view.parent == null) {
            windowManager.addView(view, params)
        } else {
            updateViewLayout(view, params)
        }
    }

    fun hideNeedBubble() {
        val view = needBubbleView ?: return
        removeView(view)
        needBubbleParams = null
    }

    fun playItemFlyAnimation(petBounds: Rect, itemText: String) {
        val view = CareItemFlyView(context, itemText)
        val measuredSize = measure(view)
        val startPoint = flyStartPoint(petBounds, measuredSize.width, measuredSize.height)
        val endX = petBounds.centerX() - measuredSize.width / 2
        val endY = petBounds.centerY() - measuredSize.height / 2
        val params = baseLayoutParams().apply {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            x = startPoint.x
            y = startPoint.y
        }

        try {
            windowManager.addView(view, params)
            activeFlyViews += view
        } catch (_: RuntimeException) {
            return
        }

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.apply {
            duration = CareItemFlyView.DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                params.x = lerp(startPoint.x, endX, progress)
                params.y = lerp(startPoint.y, endY, progress)
                view.alpha = 1f - progress
                val scale = 1f - 0.4f * progress
                view.scaleX = scale
                view.scaleY = scale
                updateViewLayout(view, params)
            }
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) = Unit
                override fun onAnimationRepeat(animation: Animator) = Unit

                override fun onAnimationCancel(animation: Animator) {
                    activeFlyAnimators -= animator
                    activeFlyViews -= view
                    removeView(view)
                }

                override fun onAnimationEnd(animation: Animator) {
                    activeFlyAnimators -= animator
                    activeFlyViews -= view
                    removeView(view)
                }
            })
            activeFlyAnimators += animator
            start()
        }
    }

    fun removeAll() {
        handler.removeCallbacksAndMessages(null)
        hideCareMenu()
        hideNeedBubble()
        activeFlyAnimators.toList().forEach { animator ->
            activeFlyAnimators -= animator
            animator.cancel()
        }
        activeFlyViews.toList().forEach { view ->
            activeFlyViews -= view
            removeView(view)
        }
    }

    private fun baseLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun menuPoint(petBounds: Rect, menuWidth: Int, menuHeight: Int): Point {
        val screen = screenSize()
        val gap = dp(8)
        val x = (petBounds.centerX() - menuWidth / 2)
            .coerceIn(0, (screen.width - menuWidth).coerceAtLeast(0))
        val belowY = petBounds.bottom + gap
        val y = if (belowY + menuHeight <= screen.height) {
            belowY
        } else {
            petBounds.top - menuHeight - gap
        }.coerceIn(0, (screen.height - menuHeight).coerceAtLeast(0))
        return Point(x, y)
    }

    private fun bubblePoint(petBounds: Rect, bubbleWidth: Int, bubbleHeight: Int): Point {
        val screen = screenSize()
        val x = (petBounds.right - bubbleWidth / 2)
            .coerceIn(0, (screen.width - bubbleWidth).coerceAtLeast(0))
        val y = (petBounds.top - bubbleHeight / 3)
            .coerceIn(0, (screen.height - bubbleHeight).coerceAtLeast(0))
        return Point(x, y)
    }

    private fun flyStartPoint(petBounds: Rect, width: Int, height: Int): Point {
        careMenuParams?.let { params ->
            return Point(params.x + width / 2, params.y + height / 2)
        }
        needBubbleParams?.let { params ->
            return Point(params.x, params.y)
        }
        return Point(
            x = petBounds.right - width / 2,
            y = petBounds.top,
        )
    }

    private fun updateViewLayout(view: View, params: WindowManager.LayoutParams) {
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) {
            // The service may remove overlay windows while an animation is finishing.
        }
    }

    private fun removeView(view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // Already detached.
        }
    }

    private fun measure(view: View): Size {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        return Size(
            width = view.measuredWidth.coerceAtLeast(dp(1)),
            height = view.measuredHeight.coerceAtLeast(dp(1)),
        )
    }

    private fun screenSize(): Size {
        val metrics = context.resources.displayMetrics
        return Size(metrics.widthPixels, metrics.heightPixels)
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

    private fun lerp(start: Int, end: Int, progress: Float): Int {
        return (start + (end - start) * progress).roundToInt()
    }

    private data class Point(val x: Int, val y: Int)
    private data class Size(val width: Int, val height: Int)

    private companion object {
        const val MENU_AUTO_HIDE_MS = 3_500L
    }
}
