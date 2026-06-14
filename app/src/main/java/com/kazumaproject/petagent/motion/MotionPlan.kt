package com.kazumaproject.petagent.motion

import com.kazumaproject.petagent.behavior.BehaviorReason
import com.kazumaproject.petagent.behavior.LocomotionMode

data class MotionPlan(
    val fromX: Int,
    val fromY: Int,
    val targetX: Int,
    val targetY: Int,
    val durationMs: Long,
    val locomotionMode: LocomotionMode,
    val animationKey: String,
    val curve: MotionCurve,
    val reason: BehaviorReason,
)

enum class MotionCurve {
    LINEAR,
    EASE_IN_OUT,
    HEAVY_PANDA_STEP,
    PANDA_QUADRUPED_LUMBER,
    OWL_ARC,
    OWL_HOP,
    OWL_TAKEOFF_GLIDE_LAND,
}
