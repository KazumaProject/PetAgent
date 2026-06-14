package com.kazumaproject.petagent.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.graphics.PixelFormat
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
import com.kazumaproject.petagent.motion.FloorZone
import com.kazumaproject.petagent.motion.MotionCurve
import com.kazumaproject.petagent.motion.MotionPlan
import com.kazumaproject.petagent.motion.MotionSequence
import com.kazumaproject.petagent.motion.PetMotionSequencePlayer
import com.kazumaproject.petagent.motion.PetPose
import com.kazumaproject.petagent.motion.PetWorld
import com.kazumaproject.petagent.motion.PoseFx
import com.kazumaproject.petagent.motion.WorldPoint
import com.kazumaproject.petagent.petpack.PetPack
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
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
    private val sequencePlayer = PetMotionSequencePlayer(handler)
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
        sequencePlayer.cancel()
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
            isMoving = autonomousAnimator?.isRunning == true || sequencePlayer.isRunning(),
        )
    }

    fun currentWorld(species: PetSpecies): PetWorld {
        val metrics = service.resources.displayMetrics
        val floorTop = (metrics.heightPixels * 0.65f).roundToInt()
        val floorBottom = (metrics.heightPixels - sizePx - dp(24)).coerceAtLeast(floorTop)
        val floorZone = FloorZone(
            top = floorTop.coerceIn(0, (metrics.heightPixels - sizePx).coerceAtLeast(0)),
            bottom = floorBottom.coerceIn(0, (metrics.heightPixels - sizePx).coerceAtLeast(0)),
            preferredY = floorBottom.coerceIn(0, (metrics.heightPixels - sizePx).coerceAtLeast(0)),
        )
        val perchPoints = when (species) {
            PetSpecies.AFRICAN_SCOPS_OWL -> listOf(
                WorldPoint(dp(18), dp(92)),
                WorldPoint(metrics.widthPixels - sizePx - dp(18), dp(96)),
                WorldPoint(dp(20), (metrics.heightPixels * 0.42f).roundToInt()),
                WorldPoint(metrics.widthPixels - sizePx - dp(22), (metrics.heightPixels * 0.48f).roundToInt()),
                WorldPoint((metrics.widthPixels - sizePx) / 2, dp(148)),
            )
            else -> listOf(
                WorldPoint(dp(24), floorZone.top),
                WorldPoint(metrics.widthPixels - sizePx - dp(24), floorZone.top),
                WorldPoint((metrics.widthPixels - sizePx) / 2, floorZone.bottom),
            )
        }.map { point ->
            WorldPoint(
                point.x.coerceIn(0, (metrics.widthPixels - sizePx).coerceAtLeast(0)),
                point.y.coerceIn(0, (metrics.heightPixels - sizePx).coerceAtLeast(0)),
            )
        }
        return PetWorld(
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            petSizePx = sizePx,
            floorZone = floorZone,
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
                MotionCurve.PANDA_QUADRUPED_LUMBER,
                MotionCurve.OWL_HOP,
                MotionCurve.OWL_TAKEOFF_GLIDE_LAND,
                -> LinearInterpolator()
                else -> AccelerateDecelerateInterpolator()
            }
            addUpdateListener { animator ->
                val rawT = animator.animatedValue as Float
                val t = movementProgress(plan.curve, rawT)
                val arcY = when (plan.curve) {
                    MotionCurve.OWL_ARC -> -dp(34) * sin(PI * rawT).toFloat()
                    MotionCurve.OWL_HOP -> -dp(22) * sin(PI * rawT).toFloat()
                    MotionCurve.OWL_TAKEOFF_GLIDE_LAND -> {
                        val glideLift = -dp(58) * sin(PI * rawT).toFloat()
                        val wingBeat = dp(5) * sin(PI * rawT * 8f).toFloat()
                        val landingSettle = if (rawT > 0.72f) {
                            dp(5) * sin(PI * (rawT - 0.72f) / 0.28f).toFloat()
                        } else {
                            0f
                        }
                        glideLift + wingBeat + landingSettle
                    }
                    MotionCurve.HEAVY_PANDA_STEP -> dp(2) * sin(PI * rawT * 6f).toFloat()
                    else -> 0f
                }
                params.x = (startX + (targetX - startX) * t).roundToInt().coerceIn(0, maxX)
                params.y = (startY + (targetY - startY) * t + arcY).roundToInt().coerceIn(0, maxY)
                applyMotionPose(view, plan.curve, rawT, targetX - startX, targetY - startY)
                updateLayout()
                listener.onPetMoved(params.toPetBounds())
                view.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    if (autonomousAnimator == animation) {
                        autonomousAnimator = null
                        resetMotionPose(view)
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (autonomousAnimator == animation) {
                        autonomousAnimator = null
                        resetMotionPose(view)
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

    fun playSequence(
        sequence: MotionSequence,
        currentPose: PetPose,
        world: PetWorld,
        onAnimation: (key: String, restart: Boolean) -> Unit,
        onFinished: () -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post {
                playSequence(
                    sequence = sequence,
                    currentPose = currentPose,
                    world = world,
                    onAnimation = onAnimation,
                    onFinished = onFinished,
                )
            }
            return
        }
        if (isMinimized || dragging) {
            onFinished()
            return
        }
        val params = layoutParams ?: run {
            onFinished()
            return
        }
        val view = petView ?: run {
            onFinished()
            return
        }

        cancelAutonomousMotion()
        var lastX = params.x
        sequencePlayer.playSequence(
            sequence = sequence,
            currentPose = currentPose,
            world = world,
            onAnimation = onAnimation,
            onMoveFrame = { x, y, poseFx, progress ->
                val maxX = (world.screenWidth - params.width).coerceAtLeast(0)
                val maxY = (world.screenHeight - params.height).coerceAtLeast(0)
                val nextX = x.coerceIn(0, maxX)
                val nextY = y.coerceIn(0, maxY)
                facing = when {
                    nextX < lastX -> Facing.LEFT
                    nextX > lastX -> Facing.RIGHT
                    else -> facing
                }
                lastX = nextX
                params.x = nextX
                params.y = nextY
                applyPoseFx(view, poseFx, progress)
                updateLayout()
                listener.onPetMoved(params.toPetBounds())
                view.invalidate()
            },
            onFinished = {
                resetMotionPose(view)
                updateLayout()
                listener.onPetMoved(params.toPetBounds())
                onFinished()
            },
        )
    }

    fun cancelAutonomousMotion() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { cancelAutonomousMotion() }
            return
        }
        autonomousAnimator?.cancel()
        autonomousAnimator = null
        sequencePlayer.cancel()
        petView?.let(::resetMotionPose)
    }

    private fun movementProgress(curve: MotionCurve, rawT: Float): Float {
        return when (curve) {
            MotionCurve.LINEAR -> rawT
            MotionCurve.PANDA_QUADRUPED_LUMBER -> quadrupedStepProgress(rawT)
            MotionCurve.EASE_IN_OUT,
            MotionCurve.HEAVY_PANDA_STEP,
            MotionCurve.OWL_ARC,
            MotionCurve.OWL_HOP,
            MotionCurve.OWL_TAKEOFF_GLIDE_LAND,
            -> smoothStep(rawT)
        }.coerceIn(0f, 1f)
    }

    private fun quadrupedStepProgress(rawT: Float): Float {
        val stepCount = 8f
        val scaled = rawT * stepCount
        val whole = floor(scaled).coerceIn(0f, stepCount - 1f)
        val local = (scaled - whole).coerceIn(0f, 1f)
        val plantedPush = if (local < 0.42f) {
            local * 0.50f / 0.42f
        } else {
            0.50f + smoothStep((local - 0.42f) / 0.58f) * 0.50f
        }
        return (whole + plantedPush) / stepCount
    }

    private fun smoothStep(t: Float): Float {
        val clamped = t.coerceIn(0f, 1f)
        return clamped * clamped * (3f - 2f * clamped)
    }

    private fun applyMotionPose(
        view: PetSpriteView,
        curve: MotionCurve,
        rawT: Float,
        deltaX: Int,
        deltaY: Int,
    ) {
        when (curve) {
            MotionCurve.PANDA_QUADRUPED_LUMBER -> {
                val footPhase = PI * rawT * 16f
                val shoulderPhase = PI * rawT * 8f
                view.translationY = dp(1) * sin(footPhase).toFloat()
                view.rotation = 0.8f * sin(shoulderPhase).toFloat()
                view.scaleX = 1f
                view.scaleY = 1f
            }
            MotionCurve.OWL_HOP -> {
                val lift = sin(PI * rawT).toFloat().coerceAtLeast(0f)
                view.translationY = -dp(2) * lift
                view.rotation = 1.0f * sin(PI * rawT).toFloat()
                view.scaleX = 1f
                view.scaleY = 1f
            }
            MotionCurve.OWL_TAKEOFF_GLIDE_LAND -> {
                val wingBeat = sin(PI * rawT * 8f).toFloat()
                val slopeDegrees = Math.toDegrees(
                    atan2(deltaY.toDouble(), max(abs(deltaX), 1).toDouble()),
                ).toFloat()
                view.translationY = dp(2) * wingBeat
                view.rotation = (slopeDegrees * 0.05f + 1.2f * sin(PI * rawT * 2f).toFloat())
                    .coerceIn(-4f, 4f)
                view.scaleX = 1f
                view.scaleY = 1f
            }
            else -> resetMotionPose(view)
        }
    }

    private fun resetMotionPose(view: PetSpriteView) {
        view.translationX = 0f
        view.translationY = 0f
        view.rotation = 0f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun applyPoseFx(
        view: PetSpriteView,
        poseFx: PoseFx,
        progress: Float,
    ) {
        if (poseFx == PoseFx.None) {
            resetMotionPose(view)
            return
        }
        val clampedProgress = progress.coerceIn(0f, 1f)
        val bobWave = if (poseFx.bobCycles > 0f) {
            sin(2.0 * PI * clampedProgress * poseFx.bobCycles).toFloat()
        } else {
            0f
        }
        val rotationWave = if (poseFx.rotationCycles > 0f) {
            sin(2.0 * PI * clampedProgress * poseFx.rotationCycles).toFloat()
        } else {
            0f
        }
        val bodyCycle = when {
            poseFx.bobCycles > 0f -> poseFx.bobCycles
            poseFx.rotationCycles > 0f -> poseFx.rotationCycles
            else -> 1f
        }
        val bodyWave = ((sin(2.0 * PI * clampedProgress * bodyCycle).toFloat() + 1f) * 0.5f)
            .coerceIn(0f, 1f)
        val squash = poseFx.squashAmount * bodyWave
        val stretch = poseFx.stretchAmount * bodyWave

        view.translationX = 0f
        view.translationY = poseFx.bobPx * bobWave
        view.rotation = poseFx.rotationDeg * rotationWave
        view.scaleX = (1f + squash - stretch * 0.45f).coerceIn(0.92f, 1.08f)
        view.scaleY = (1f - squash + stretch).coerceIn(0.92f, 1.08f)
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
