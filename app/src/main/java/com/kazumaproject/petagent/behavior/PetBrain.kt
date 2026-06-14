package com.kazumaproject.petagent.behavior

import com.kazumaproject.petagent.breakreminder.BreakReminderContext
import com.kazumaproject.petagent.breakreminder.BreakReminderDecision
import com.kazumaproject.petagent.breakreminder.BreakReminderEngine
import com.kazumaproject.petagent.breakreminder.BreakReminderRepository
import com.kazumaproject.petagent.data.behavior.PetBehaviorRepository
import com.kazumaproject.petagent.data.memory.PetMemoryRepository
import com.kazumaproject.petagent.motion.PetMotionPlanner
import com.kazumaproject.petagent.motion.PetPose
import com.kazumaproject.petagent.runtime.PetState

class PetBrain(
    private val behaviorProfile: SpeciesBehaviorProfile,
    private val motionPlanner: PetMotionPlanner,
    private val breakReminderEngine: BreakReminderEngine,
    private val breakReminderRepository: BreakReminderRepository,
    private val petMemoryRepository: PetMemoryRepository,
    private val petBehaviorRepository: PetBehaviorRepository,
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

        val reminderDecision = breakReminderEngine.decide(
            settings = context.breakReminderSettings,
            context = BreakReminderContext(
                petId = context.petId,
                overlayVisible = context.overlayVisible,
                isMinimized = context.isMinimized,
                isDragging = context.isDragging,
                nowWallClockMs = context.nowWallClockMs,
            ),
        )
        when (reminderDecision) {
            is BreakReminderDecision.ShowReminder -> {
                petMemoryRepository.incrementReminderShown(
                    petId = context.petId,
                    species = context.speciesName,
                    nowMs = context.nowWallClockMs,
                )
                petBehaviorRepository.record(
                    petId = context.petId,
                    species = context.speciesName,
                    occurredAtMs = context.nowWallClockMs,
                    behaviorId = PetBehaviorIds.BREAK_REMINDER_SHOWN,
                    reason = BehaviorReason.BREAK_DUE,
                )
                return PetDecision.ShowBreakReminder(
                    message = reminderDecision.message,
                    activeMinutes = reminderDecision.activeMinutes,
                    tone = reminderDecision.tone,
                )
            }
            is BreakReminderDecision.AlmostDue -> {
                val leadMs = behaviorProfile.breakPrepareLeadTimeMinutes * ONE_MINUTE_MS
                val shouldPrepare = context.behaviorSettings.approachBeforeBreakEnabled &&
                    reminderDecision.remainingMs <= leadMs &&
                    nowMs - state.need.lastReminderProposalAtMs >= PREPARE_REPEAT_GUARD_MS
                if (shouldPrepare) {
                    val plan = if (context.behaviorSettings.autonomousMoveEnabled) {
                        motionPlanner.planBreakApproach(behaviorProfile, pose, context.world)
                    } else {
                        null
                    }
                    petBehaviorRepository.record(
                        petId = context.petId,
                        species = context.speciesName,
                        occurredAtMs = context.nowWallClockMs,
                        behaviorId = PetBehaviorIds.PREPARE_BREAK_REMINDER,
                        locomotionMode = plan?.locomotionMode,
                        reason = BehaviorReason.PREPARE_BREAK,
                        fromX = plan?.fromX,
                        fromY = plan?.fromY,
                        toX = plan?.targetX,
                        toY = plan?.targetY,
                    )
                    return PetDecision.PrepareBreakReminder(
                        plan = plan,
                        animationKey = motionPlanner.chooseAvailableAnimation(
                            listOf("think", "look_left", "look_right", behaviorProfile.preferredRestAnimationKey),
                        ),
                    )
                }
            }
            BreakReminderDecision.None -> Unit
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
                PetBehaviorFrequency.LOW -> 240_000L
                PetBehaviorFrequency.NORMAL -> 150_000L
                PetBehaviorFrequency.HIGH -> 75_000L
            }
            PetSpecies.AFRICAN_SCOPS_OWL -> when (frequency) {
                PetBehaviorFrequency.LOW -> 360_000L
                PetBehaviorFrequency.NORMAL -> 240_000L
                PetBehaviorFrequency.HIGH -> 120_000L
            }
            PetSpecies.UNKNOWN -> when (frequency) {
                PetBehaviorFrequency.LOW -> 240_000L
                PetBehaviorFrequency.NORMAL -> 150_000L
                PetBehaviorFrequency.HIGH -> 75_000L
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

    private companion object {
        const val ONE_MINUTE_MS = 60_000L
        const val PREPARE_REPEAT_GUARD_MS = 60_000L
    }
}
