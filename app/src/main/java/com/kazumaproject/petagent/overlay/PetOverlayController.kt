package com.kazumaproject.petagent.overlay

import android.app.Service
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import com.kazumaproject.petagent.petpack.PetPack
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PetOverlayController(
    private val service: Service,
    private val petPack: PetPack,
    private val listener: Listener,
    initialSizeDp: Int = petPack.manifest.defaultSizeDp,
) {
    interface Listener {
        fun onPetTapped()
        fun onDragStarted()
        fun onDragEnded()
        fun onMinimizedChanged(minimized: Boolean)
    }

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
    private var sizeDp = initialSizeDp.coerceIn(petPack.manifest.minSizeDp, petPack.manifest.maxSizeDp)
    private var sizePx = dp(sizeDp)
    private var petView: PetSpriteView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var longPressConsumed = false
    private var isMinimized = false
    private var restoreX = 0
    private var restoreY = 0

    val view: PetSpriteView?
        get() = petView

    fun show() {
        if (petView != null) return

        val view = PetSpriteView(service, petPack)
        val metrics = service.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (metrics.widthPixels - sizePx - dp(24)).coerceAtLeast(0)
            y = dp(140)
        }

        view.setOnTouchListener { _, event -> handleTouch(event) }
        petView = view
        layoutParams = params
        windowManager.addView(view, params)
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        val view = petView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The window may already be detached during service teardown.
        }
        view.unload()
        petView = null
        layoutParams = null
    }

    fun toggleMinimized() {
        setMinimized(!isMinimized)
    }

    fun updateSize(sizeDp: Int) {
        val clampedSizeDp = sizeDp.coerceIn(petPack.manifest.minSizeDp, petPack.manifest.maxSizeDp)
        val newSizePx = dp(clampedSizeDp)
        if (newSizePx == this.sizePx) return

        this.sizeDp = clampedSizeDp
        this.sizePx = newSizePx

        val params = layoutParams ?: return
        params.width = newSizePx
        params.height = newSizePx

        val metrics = service.resources.displayMetrics
        if (isMinimized) {
            val visibleStrip = max(dp(22), newSizePx / 3)
            params.x = if (params.x < metrics.widthPixels / 2) {
                -(newSizePx - visibleStrip)
            } else {
                metrics.widthPixels - visibleStrip
            }
        } else {
            params.x = params.x.coerceIn(0, (metrics.widthPixels - newSizePx).coerceAtLeast(0))
        }
        params.y = params.y.coerceIn(0, (metrics.heightPixels - newSizePx).coerceAtLeast(0))

        updateLayout()
    }

    fun getPetId(): String = petPack.manifest.petId

    fun setMinimized(minimized: Boolean) {
        val params = layoutParams ?: return
        if (isMinimized == minimized) return
        isMinimized = minimized

        if (minimized) {
            restoreX = params.x
            restoreY = params.y
            val metrics = service.resources.displayMetrics
            val visibleStrip = max(dp(22), sizePx / 3)
            params.x = if (params.x < metrics.widthPixels / 2) {
                -(sizePx - visibleStrip)
            } else {
                metrics.widthPixels - visibleStrip
            }
            params.y = params.y.coerceIn(0, (metrics.heightPixels - sizePx).coerceAtLeast(0))
        } else {
            val metrics = service.resources.displayMetrics
            params.x = restoreX.coerceIn(0, (metrics.widthPixels - sizePx).coerceAtLeast(0))
            params.y = restoreY.coerceIn(0, (metrics.heightPixels - sizePx).coerceAtLeast(0))
        }

        updateLayout()
        listener.onMinimizedChanged(minimized)
    }

    private fun handleTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = params.x
                startY = params.y
                dragging = false
                longPressConsumed = false
                handler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    handler.removeCallbacks(longPressRunnable)
                    if (isMinimized) {
                        setMinimized(false)
                    }
                    dragging = true
                    listener.onDragStarted()
                }
                if (dragging) {
                    val metrics = service.resources.displayMetrics
                    params.x = (startX + dx.roundToInt()).coerceIn(0, (metrics.widthPixels - sizePx).coerceAtLeast(0))
                    params.y = (startY + dy.roundToInt()).coerceIn(0, (metrics.heightPixels - sizePx).coerceAtLeast(0))
                    updateLayout()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (dragging) {
                    listener.onDragEnded()
                } else if (!longPressConsumed) {
                    if (isMinimized) {
                        setMinimized(false)
                    } else {
                        listener.onPetTapped()
                    }
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (dragging) {
                    listener.onDragEnded()
                }
                dragging = false
                return true
            }
        }
        return false
    }

    private val longPressRunnable = Runnable {
        if (!dragging) {
            longPressConsumed = true
            toggleMinimized()
        }
    }

    private fun updateLayout() {
        val view = petView ?: return
        val params = layoutParams ?: return
        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) {
            // The overlay can disappear while the foreground service is stopping.
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
        return (value * service.resources.displayMetrics.density).roundToInt()
    }
}
