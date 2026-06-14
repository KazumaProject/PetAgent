package com.kazumaproject.petagent.motion

import com.kazumaproject.petagent.behavior.BehaviorIntent
import com.kazumaproject.petagent.behavior.BehaviorReason
import com.kazumaproject.petagent.behavior.PetSpecies
import com.kazumaproject.petagent.petpack.PetPack
import kotlin.math.max

class PetMotionDirector(
    private val petPack: PetPack,
    private val planner: PetMotionPlanner,
) {
    fun buildSequence(
        intent: BehaviorIntent,
        pose: PetPose,
        world: PetWorld,
        species: String,
    ): MotionSequence {
        val reason = when (intent) {
            is BehaviorIntent.ApproachForBreakReminder -> intent.reason
            is BehaviorIntent.Wander -> intent.reason
            BehaviorIntent.IdleMicroMotion,
            BehaviorIntent.LookAround,
            BehaviorIntent.Nap,
            is BehaviorIntent.ReactToTap,
            BehaviorIntent.SocialPrompt,
            -> BehaviorReason.IDLE
        }

        return when (intent) {
            is BehaviorIntent.Wander,
            is BehaviorIntent.ApproachForBreakReminder,
            -> when (PetSpecies.from(species)) {
                PetSpecies.PANDA -> buildPandaWander(pose, world, reason)
                PetSpecies.AFRICAN_SCOPS_OWL -> buildOwlWander(pose, world, reason)
                PetSpecies.UNKNOWN -> idleSequence(reason)
            }
            BehaviorIntent.IdleMicroMotion -> idleSequence(reason)
            BehaviorIntent.LookAround -> lookAroundSequence(pose, reason)
            BehaviorIntent.Nap -> animationSequence("nap", "sleep", "idle", 2_500L, reason)
            is BehaviorIntent.ReactToTap -> lookAroundSequence(pose, BehaviorReason.USER_TAPPED)
            BehaviorIntent.SocialPrompt -> animationSequence("social_prompt", "happy", "idle", 1_100L, reason)
        }
    }

    private fun buildPandaWander(
        pose: PetPose,
        world: PetWorld,
        reason: BehaviorReason,
    ): MotionSequence {
        val plan = planner.planPandaWander(pose, world, reason)
        val direction = directionFor(pose, plan.targetX)
        val suffix = if (direction == Facing.LEFT) "left" else "right"
        val lookFallback = "look_$suffix"
        val walkFallback = "walk_$suffix"
        val lumberKey = key("lumber_$suffix", walkFallback)
        val idleKey = key("idle_smooth", "idle")
        return MotionSequence(
            id = "panda_wander_${reason.name.lowercase()}",
            reason = reason,
            phases = listOf(
                MotionPhase.Animation(
                    animationKey = key("turn_$suffix", lookFallback),
                    durationMs = durationFor(key("turn_$suffix", lookFallback), 420L),
                ),
                MotionPhase.Animation(
                    animationKey = key("prepare_walk_$suffix", walkFallback),
                    durationMs = durationFor(key("prepare_walk_$suffix", walkFallback), 520L),
                ),
                MotionPhase.Move(
                    targetX = plan.targetX,
                    targetY = plan.targetY,
                    durationMs = plan.durationMs,
                    animationKey = lumberKey,
                    curve = MotionCurve.PANDA_QUADRUPED_LUMBER,
                    poseFx = PoseFx(
                        bobPx = 2f,
                        bobCycles = 4f,
                        rotationDeg = 1.2f,
                        rotationCycles = 2f,
                        squashAmount = 0.025f,
                    ),
                ),
                MotionPhase.Animation(
                    animationKey = key("stop_$suffix", "idle"),
                    durationMs = durationFor(key("stop_$suffix", "idle"), 480L),
                ),
                MotionPhase.Settle(
                    animationKey = idleKey,
                    durationMs = 650L,
                ),
            ),
        )
    }

    private fun buildOwlWander(
        pose: PetPose,
        world: PetWorld,
        reason: BehaviorReason,
    ): MotionSequence {
        val plan = planner.planOwlWander(pose, world, reason) ?: return idleSequence(reason)
        val direction = directionFor(pose, plan.targetX)
        val suffix = if (direction == Facing.LEFT) "left" else "right"
        val lookFallback = "look_$suffix"
        val flightFallback = "fly_$suffix"
        return MotionSequence(
            id = "owl_wander_${reason.name.lowercase()}",
            reason = reason,
            phases = listOf(
                MotionPhase.Animation(
                    animationKey = key("head_tilt_$suffix", lookFallback),
                    durationMs = durationFor(key("head_tilt_$suffix", lookFallback), 520L),
                ),
                MotionPhase.Animation(
                    animationKey = key("takeoff_$suffix", flightFallback),
                    durationMs = durationFor(key("takeoff_$suffix", flightFallback), 560L),
                ),
                MotionPhase.Move(
                    targetX = plan.targetX,
                    targetY = plan.targetY,
                    durationMs = max(900L, plan.durationMs),
                    animationKey = key("glide_$suffix", flightFallback),
                    curve = MotionCurve.OWL_TAKEOFF_GLIDE_LAND,
                    poseFx = PoseFx(
                        bobPx = 5f,
                        bobCycles = 6f,
                        rotationDeg = 3f,
                        rotationCycles = 2f,
                        stretchAmount = 0.018f,
                    ),
                ),
                MotionPhase.Animation(
                    animationKey = key("landing_$suffix", "land"),
                    durationMs = durationFor(key("landing_$suffix", "land"), 560L),
                ),
                MotionPhase.Animation(
                    animationKey = key("settle", "idle"),
                    durationMs = durationFor(key("settle", "idle"), 520L),
                ),
                MotionPhase.Settle(
                    animationKey = key("perch_idle_loop", "idle"),
                    durationMs = 850L,
                ),
            ),
        )
    }

    private fun idleSequence(reason: BehaviorReason): MotionSequence {
        return MotionSequence(
            id = "idle_${reason.name.lowercase()}",
            reason = reason,
            phases = listOf(
                MotionPhase.Settle(
                    animationKey = key("idle_smooth", "idle"),
                    durationMs = 650L,
                ),
            ),
        )
    }

    private fun lookAroundSequence(pose: PetPose, reason: BehaviorReason): MotionSequence {
        val suffix = if (pose.facing == Facing.LEFT) "left" else "right"
        return animationSequence(
            id = "look_around",
            preferred = "look_$suffix",
            fallback = "idle",
            durationMs = 650L,
            reason = reason,
        )
    }

    private fun animationSequence(
        id: String,
        preferred: String,
        fallback: String,
        durationMs: Long,
        reason: BehaviorReason,
    ): MotionSequence {
        val animationKey = key(preferred, fallback)
        return MotionSequence(
            id = id,
            reason = reason,
            phases = listOf(
                MotionPhase.Animation(
                    animationKey = animationKey,
                    durationMs = durationFor(animationKey, durationMs),
                ),
            ),
        )
    }

    private fun directionFor(pose: PetPose, targetX: Int): Facing {
        return when {
            targetX < pose.x -> Facing.LEFT
            targetX > pose.x -> Facing.RIGHT
            pose.facing == Facing.LEFT -> Facing.LEFT
            else -> Facing.RIGHT
        }
    }

    private fun key(preferred: String, fallback: String): String {
        return petPack.resolveAnimationKeyOrFallback(preferred, fallback)
    }

    private fun durationFor(animationKey: String, fallbackMs: Long): Long {
        val animation = petPack.animations[animationKey] ?: return fallbackMs
        if (animation.loop) return fallbackMs
        return ((animation.frameCount * 1_000L) / animation.fps.coerceAtLeast(1))
            .coerceAtLeast(220L)
    }
}
