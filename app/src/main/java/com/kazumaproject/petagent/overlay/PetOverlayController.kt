package com.kazumaproject.petagent.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.kazumaproject.petagent.behavior.PetSpecies
import com.kazumaproject.petagent.motion.Facing
import com.kazumaproject.petagent.motion.MotionCurve
import com.kazumaproject.petagent.motion.MotionPlan
import com.kazumaproject.petagent.motion.PetPose
import com.kazumaproject.petagent.motion.PetWorld
import com.kazumaproject.petagent.petpack.PetPack
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class PetOverlayController(
    private val service: Service,
    private val petPack: PetPack,
    private val listener: Listener,
    initialSizeDp: Int = petPack.manifest.defaultSizeDp,
) {
    interface Listener {
        fun onPetTapped(petBounds: Rect)
        fun onDragStarted(petBounds: Rect)
        fun onPetMoved(petBounds: Rect)
        fun onDragEnded(petBounds: Rect)
        fun onMinimizedChanged(minimized: Boolean, petBounds: Rect)
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
    private var facing = Facing.FORWARD
    private var autonomousAnimator: ValueAnimator? = null

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
        autonomousAnimator?.cancel()
        autonomousAnimator = null
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
        cancelAutonomousMotion()
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
        listener.onPetMoved(params.toPetBounds())
    }

    fun getPetId(): String = petPack.manifest.petId

    fun currentPetBounds(): Rect? {
        val params = layoutParams ?: return null
        return params.toPetBounds()
    }

    fun currentPose(): PetPose {
        val params = layoutParams
        return PetPose(
            x = params?.x ?: 0,
            y = params?.y ?: 0,
            width = params?.width ?: sizePx,
            height = params?.height ?: sizePx,
            facing = facing,
            isMoving = autonomousAnimator?.isRunning == true,
        )
    }

    fun currentWorld(species: PetSpecies): PetWorld {
        val metrics = service.resources.displayMetrics
        val floorTop = (metrics.heightPixels * 0.65f).roundToInt()
        val floorBottom = (metrics.heightPixels - sizePx).coerceAtLeast(floorTop)
        val perchPoints = when (species) {
            PetSpecies.AFRICAN_SCOPS_OWL -> listOf(
                Point(dp(18), dp(92)),
                Point(metrics.widthPixels - sizePx - dp(18), dp(96)),
                Point(dp(20), (metrics.heightPixels * 0.42f).roundToInt()),
                Point(metrics.widthPixels - sizePx - dp(22), (metrics.heightPixels * 0.48f).roundToInt()),
                Point((metrics.widthPixels - sizePx) / 2, dp(148)),
            )
            else -> listOf(
                Point(dp(24), floorTop),
                Point(metrics.widthPixels - sizePx - dp(24), floorTop),
                Point((metrics.widthPixels - sizePx) / 2, floorBottom),
            )
        }.map { point ->
            Point(
                point.x.coerceIn(0, (metrics.widthPixels - sizePx).coerceAtLeast(0)),
                point.y.coerceIn(0, (metrics.heightPixels - sizePx).coerceAtLeast(0)),
            )
        }
        return PetWorld(
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            petSizePx = sizePx,
            floorBandTop = floorTop,
            floorBandBottom = floorBottom,
            perchPoints = perchPoints,
        )
    }

    fun animateTo(plan: MotionPlan, onFinished: (() -> Unit)? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { animateTo(plan, onFinished) }
            return
        }
        if (isMinimized || dragging) {
            onFinished?.invoke()
            return
        }
        val params = layoutParams ?: return
        val view = petView ?: return
        cancelAutonomousMotion()

        val metrics = service.resources.displayMetrics
        val maxX = (metrics.widthPixels - sizePx).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - sizePx).coerceAtLeast(0)
        val startX = params.x
        val startY = params.y
        val targetX = plan.targetX.coerceIn(0, maxX)
        val targetY = plan.targetY.coerceIn(0, maxY)
        facing = when {
            targetX < startX -> Facing.LEFT
            targetX > startX -> Facing.RIGHT
            else -> Facing.FORWARD
        }

        autonomousAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = plan.durationMs
            interpolator = when (plan.curve) {
                MotionCurve.LINEAR -> LinearInterpolator()
                else -> AccelerateDecelerateInterpolator()
            }
            addUpdateListener { animator ->
                val rawT = animator.animatedValue as Float
                val t = when (plan.curve) {
                    MotionCurve.LINEAR -> rawT
                    MotionCurve.EASE_IN_OUT,
                    MotionCurve.HEAVY_PANDA_STEP,
                    MotionCurve.OWL_ARC,
                    -> rawT * rawT * (3f - 2f * rawT)
                }
                val arcY = when (plan.curve) {
                    MotionCurve.OWL_ARC -> -dp(34) * sin(PI * rawT).toFloat()
                    MotionCurve.HEAVY_PANDA_STEP -> dp(3) * sin(PI * rawT * 6f).toFloat()
                    else -> 0f
                }
                params.x = (startX + (targetX - startX) * t).roundToInt().coerceIn(0, maxX)
                params.y = (startY + (targetY - startY) * t + arcY).roundToInt().coerceIn(0, maxY)
                updateLayout()
                listener.onPetMoved(params.toPetBounds())
                view.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    if (autonomousAnimator == animation) {
                        autonomousAnimator = null
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (autonomousAnimator == animation) {
                        autonomousAnimator = null
                        params.x = targetX
                        params.y = targetY
                        updateLayout()
                        listener.onPetMoved(params.toPetBounds())
                        onFinished?.invoke()
                    }
                }
            })
            start()
        }
    }

    fun cancelAutonomousMotion() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { cancelAutonomousMotion() }
            return
        }
        autonomousAnimator?.cancel()
        autonomousAnimator = null
    }

    fun isMinimized(): Boolean = isMinimized

    fun isDragging(): Boolean = dragging

    fun setMinimized(minimized: Boolean) {
        val params = layoutParams ?: return
        if (isMinimized == minimized) return
        cancelAutonomousMotion()
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
        listener.onMinimizedChanged(minimized, params.toPetBounds())
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
                    cancelAutonomousMotion()
                    dragging = true
                    listener.onDragStarted(params.toPetBounds())
                }
                if (dragging) {
                    val metrics = service.resources.displayMetrics
                    params.x = (startX + dx.roundToInt()).coerceIn(0, (metrics.widthPixels - sizePx).coerceAtLeast(0))
                    params.y = (startY + dy.roundToInt()).coerceIn(0, (metrics.heightPixels - sizePx).coerceAtLeast(0))
                    updateLayout()
                    listener.onPetMoved(params.toPetBounds())
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPressRunnable)
                if (dragging) {
                    listener.onDragEnded(params.toPetBounds())
                } else if (!longPressConsumed) {
                    if (isMinimized) {
                        setMinimized(false)
                    } else {
                        listener.onPetTapped(params.toPetBounds())
                    }
                }
                dragging = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPressRunnable)
                if (dragging) {
                    listener.onDragEnded(params.toPetBounds())
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { updateLayout() }
            return
        }
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

    private fun WindowManager.LayoutParams.toPetBounds(): Rect {
        return Rect(x, y, x + width, y + height)
    }
}
