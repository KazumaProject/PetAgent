package com.kazumaproject.petagent.motion

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

class PetMotionSequencePlayer(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var animator: ValueAnimator? = null
    private var pendingRunnable: Runnable? = null
    private var playToken = 0
    private var running = false

    fun isRunning(): Boolean = running

    fun playSequence(
        sequence: MotionSequence,
        currentPose: PetPose,
        world: PetWorld,
        onAnimation: (key: String, restart: Boolean) -> Unit,
        onMoveFrame: (x: Int, y: Int, poseFx: PoseFx, progress: Float) -> Unit,
        onFinished: () -> Unit,
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post {
                playSequence(
                    sequence = sequence,
                    currentPose = currentPose,
                    world = world,
                    onAnimation = onAnimation,
                    onMoveFrame = onMoveFrame,
                    onFinished = onFinished,
                )
            }
            return
        }

        cancel()
        val token = ++playToken
        running = true

        val maxX = (world.screenWidth - currentPose.width).coerceAtLeast(0)
        val maxY = (world.screenHeight - currentPose.height).coerceAtLeast(0)
        var currentX = currentPose.x.coerceIn(0, maxX)
        var currentY = currentPose.y.coerceIn(0, maxY)
        var finished = false

        fun resetPose() {
            onMoveFrame(currentX, currentY, PoseFx.None, 1f)
        }

        fun finishOnce() {
            if (finished || playToken != token) return
            finished = true
            running = false
            animator = null
            pendingRunnable = null
            resetPose()
            onFinished()
        }

        fun scheduleNext(delayMs: Long, next: () -> Unit) {
            val runnable = Runnable {
                if (playToken == token) next()
            }
            pendingRunnable = runnable
            handler.postDelayed(runnable, delayMs.coerceAtLeast(0L))
        }

        fun playTimedPose(
            durationMs: Long,
            poseFx: PoseFx,
            next: () -> Unit,
        ) {
            if (durationMs <= 0L) {
                resetPose()
                next()
                return
            }
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                interpolator = LinearInterpolator()
                addUpdateListener { valueAnimator ->
                    if (playToken != token) return@addUpdateListener
                    val progress = valueAnimator.animatedValue as Float
                    onMoveFrame(currentX, currentY, poseFx, progress)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (playToken != token) return
                        animator = null
                        resetPose()
                        next()
                    }
                })
                start()
            }
        }

        fun playMove(
            phase: MotionPhase.Move,
            next: () -> Unit,
        ) {
            val startX = currentX
            val startY = currentY
            val targetX = phase.targetX.coerceIn(0, maxX)
            val targetY = phase.targetY.coerceIn(0, maxY)
            onAnimation(phase.animationKey, phase.restartAnimation)
            if (phase.durationMs <= 0L) {
                currentX = targetX
                currentY = targetY
                resetPose()
                next()
                return
            }

            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = phase.durationMs
                interpolator = LinearInterpolator()
                addUpdateListener { valueAnimator ->
                    if (playToken != token) return@addUpdateListener
                    val rawProgress = valueAnimator.animatedValue as Float
                    val movementProgress = movementProgress(phase.curve, rawProgress)
                    val curveYOffset = motionCurveYOffset(phase.curve, rawProgress, world)
                    val frameX = (startX + (targetX - startX) * movementProgress)
                        .roundToInt()
                        .coerceIn(0, maxX)
                    val frameY = (startY + (targetY - startY) * movementProgress + curveYOffset)
                        .roundToInt()
                        .coerceIn(0, maxY)
                    onMoveFrame(frameX, frameY, phase.poseFx, rawProgress)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (playToken != token) return
                        animator = null
                        currentX = targetX
                        currentY = targetY
                        resetPose()
                        next()
                    }
                })
                start()
            }
        }

        fun playPhase(index: Int) {
            if (playToken != token) return
            val phase = sequence.phases.getOrNull(index)
            if (phase == null) {
                finishOnce()
                return
            }
            when (phase) {
                is MotionPhase.Animation -> {
                    onAnimation(phase.animationKey, phase.restart)
                    scheduleNext(phase.durationMs) {
                        resetPose()
                        playPhase(index + 1)
                    }
                }
                is MotionPhase.Move -> playMove(phase) {
                    playPhase(index + 1)
                }
                is MotionPhase.Settle -> {
                    onAnimation(phase.animationKey, phase.restart)
                    playTimedPose(phase.durationMs, phase.poseFx) {
                        playPhase(index + 1)
                    }
                }
            }
        }

        playPhase(0)
    }

    fun cancel() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { cancel() }
            return
        }
        playToken++
        pendingRunnable?.let(handler::removeCallbacks)
        pendingRunnable = null
        animator?.cancel()
        animator = null
        running = false
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

    private fun motionCurveYOffset(
        curve: MotionCurve,
        rawT: Float,
        world: PetWorld,
    ): Float {
        return when (curve) {
            MotionCurve.OWL_ARC -> -world.petSizePx * 0.28f * sin(PI * rawT).toFloat()
            MotionCurve.OWL_HOP -> -world.petSizePx * 0.18f * sin(PI * rawT).toFloat()
            MotionCurve.OWL_TAKEOFF_GLIDE_LAND -> {
                val glideLift = -world.petSizePx * 0.42f * sin(PI * rawT).toFloat()
                val wingBeat = world.petSizePx * 0.035f * sin(PI * rawT * 8f).toFloat()
                val landingSettle = if (rawT > 0.72f) {
                    world.petSizePx * 0.035f * sin(PI * (rawT - 0.72f) / 0.28f).toFloat()
                } else {
                    0f
                }
                glideLift + wingBeat + landingSettle
            }
            MotionCurve.HEAVY_PANDA_STEP -> world.petSizePx * 0.02f * sin(PI * rawT * 6f).toFloat()
            MotionCurve.LINEAR,
            MotionCurve.EASE_IN_OUT,
            MotionCurve.PANDA_QUADRUPED_LUMBER,
            -> 0f
        }
    }
}
