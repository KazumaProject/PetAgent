package com.kazumaproject.petagent.behavior

import com.kazumaproject.petagent.breakreminder.BreakReminderTone
import com.kazumaproject.petagent.motion.MotionPlan

sealed interface PetDecision {
    data object None : PetDecision

    data class PlayAnimation(
        val animationKey: String,
        val durationMs: Long,
        val behaviorId: String,
        val reason: BehaviorReason,
    ) : PetDecision

    data class MoveTo(
        val plan: MotionPlan,
    ) : PetDecision

    data class PrepareBreakReminder(
        val plan: MotionPlan?,
        val animationKey: String?,
    ) : PetDecision

    data class ShowBreakReminder(
        val message: String,
        val activeMinutes: Int,
        val tone: BreakReminderTone,
    ) : PetDecision

    data object ShowBreakStatusPanel : PetDecision
}
