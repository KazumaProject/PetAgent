package com.kazumaproject.petagent.motion

import com.kazumaproject.petagent.behavior.BehaviorReason

sealed interface MotionPhase {
    data class Animation(
        val animationKey: String,
        val durationMs: Long,
        val restart: Boolean = true,
    ) : MotionPhase

    data class Move(
        val targetX: Int,
        val targetY: Int,
        val durationMs: Long,
        val animationKey: String,
        val curve: MotionCurve,
        val poseFx: PoseFx = PoseFx.None,
        val restartAnimation: Boolean = true,
    ) : MotionPhase

    data class Settle(
        val animationKey: String,
        val durationMs: Long,
        val poseFx: PoseFx = PoseFx.None,
        val restart: Boolean = true,
    ) : MotionPhase
}

data class MotionSequence(
    val id: String,
    val phases: List<MotionPhase>,
    val reason: BehaviorReason,
)

data class PoseFx(
    val bobPx: Float = 0f,
    val bobCycles: Float = 0f,
    val rotationDeg: Float = 0f,
    val rotationCycles: Float = 0f,
    val squashAmount: Float = 0f,
    val stretchAmount: Float = 0f,
) {
    companion object {
        val None = PoseFx()
    }
}
