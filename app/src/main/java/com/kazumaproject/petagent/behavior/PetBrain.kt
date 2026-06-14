package com.kazumaproject.petagent.behavior

import com.kazumaproject.petagent.motion.PetMotionPlanner
import com.kazumaproject.petagent.motion.PetPose
import com.kazumaproject.petagent.runtime.PetState

class PetBrain(
    private val behaviorProfile: SpeciesBehaviorProfile,
    private val motionPlanner: PetMotionPlanner,
) {
    suspend fun think(
        state: PetState,
        context: PetContext,
        pose: PetPose,
        nowMs: Long,
    ): PetDecision {
        if (context.isMinimized || context.isDragging || context.hasTransientAnimation || pose.isMoving) {
            return PetDecision.None
        }

        val behaviorSettings = context.behaviorSettings
        if (!behaviorSettings.autonomousBehaviorEnabled || state.need.isSleeping) {
            return PetDecision.None
        }

        val moveIntervalMs = moveIntervalMs(behaviorProfile.species, behaviorSettings.frequency)
        if (
            behaviorSettings.autonomousMoveEnabled &&
            nowMs - state.need.lastAutonomousMoveAtMs >= moveIntervalMs
        ) {
            val plan = motionPlanner.planAutonomousMove(
                profile = behaviorProfile,
                pose = pose,
                world = context.world,
                reason = BehaviorReason.AUTONOMOUS_WANDER,
            )
            if (plan != null) return PetDecision.MoveTo(plan)
        }

        val gestureIntervalMs = gestureIntervalMs(behaviorSettings.frequency)
        if (nowMs - state.need.lastAutonomousGestureAtMs >= gestureIntervalMs) {
            val gesture = motionPlanner.chooseAutonomousGesture(behaviorProfile, behaviorSettings)
            return PetDecision.PlayAnimation(
                animationKey = gesture.animationKey,
                durationMs = gesture.durationMs,
                behaviorId = gesture.behaviorId,
                reason = BehaviorReason.SPECIES_NATURAL_BEHAVIOR,
            )
        }

        return PetDecision.None
    }

    private fun moveIntervalMs(species: PetSpecies, frequency: PetBehaviorFrequency): Long {
        return when (species) {
            PetSpecies.PANDA -> when (frequency) {
                PetBehaviorFrequency.LOW -> 25_000L
                PetBehaviorFrequency.NORMAL -> 12_000L
                PetBehaviorFrequency.HIGH -> 6_000L
            }
            PetSpecies.AFRICAN_SCOPS_OWL -> when (frequency) {
                PetBehaviorFrequency.LOW -> 30_000L
                PetBehaviorFrequency.NORMAL -> 14_000L
                PetBehaviorFrequency.HIGH -> 7_000L
            }
            PetSpecies.UNKNOWN -> when (frequency) {
                PetBehaviorFrequency.LOW -> 25_000L
                PetBehaviorFrequency.NORMAL -> 12_000L
                PetBehaviorFrequency.HIGH -> 6_000L
            }
        }
    }

    private fun gestureIntervalMs(frequency: PetBehaviorFrequency): Long {
        return when (frequency) {
            PetBehaviorFrequency.LOW -> 90_000L
            PetBehaviorFrequency.NORMAL -> 45_000L
            PetBehaviorFrequency.HIGH -> 20_000L
        }
    }
}
