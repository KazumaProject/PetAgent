package com.kazumaproject.petagent.motion

import android.graphics.Point
import com.kazumaproject.petagent.behavior.BehaviorReason
import com.kazumaproject.petagent.behavior.LocomotionMode
import com.kazumaproject.petagent.behavior.PetBehaviorFrequency
import com.kazumaproject.petagent.behavior.PetBehaviorSettings
import com.kazumaproject.petagent.behavior.PetSpecies
import com.kazumaproject.petagent.behavior.SpeciesBehaviorProfile
import com.kazumaproject.petagent.petpack.PetPack
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class PetMotionPlanner(
    private val petPack: PetPack,
    private val random: Random = Random.Default,
) {
    fun chooseAvailableAnimation(
        preferred: List<String>,
        fallback: String = "idle",
    ): String {
        return preferred.firstOrNull { petPack.animations.containsKey(it) }
            ?: fallback.takeIf { petPack.animations.containsKey(it) }
            ?: petPack.animations.keys.firstOrNull()
            ?: fallback
    }

    fun chooseAutonomousGesture(
        profile: SpeciesBehaviorProfile,
        settings: PetBehaviorSettings,
    ): AutonomousGesture {
        val species = profile.species
        val sleepAllowed = settings.frequency == PetBehaviorFrequency.LOW
        val candidates = when (species) {
            PetSpecies.PANDA -> if (sleepAllowed) {
                listOf("sit", "look_left", "look_right", "sleep", "blink", "idle")
            } else {
                listOf("sit", "look_left", "look_right", "blink", "idle")
            }
            PetSpecies.AFRICAN_SCOPS_OWL -> if (sleepAllowed) {
                listOf("look_left", "look_right", "think", "sleep", "blink", "idle")
            } else {
                listOf("look_left", "look_right", "think", "blink", "idle")
            }
            PetSpecies.UNKNOWN -> profile.idleAnimationKeys
        }
        val animation = chooseAvailableAnimation(candidates)
        val behaviorId = when {
            species == PetSpecies.PANDA && animation == "sit" -> "PANDA_SIT"
            species == PetSpecies.PANDA && animation == "sleep" -> "PANDA_NAP"
            species == PetSpecies.AFRICAN_SCOPS_OWL && animation == "sleep" -> "OWL_SLEEP"
            species == PetSpecies.AFRICAN_SCOPS_OWL -> "OWL_HEAD_SCAN"
            else -> "AUTO_SLEEP"
        }
        return AutonomousGesture(
            animationKey = animation,
            durationMs = if (animation == "sleep") 4_000L else 1_200L,
            behaviorId = behaviorId,
        )
    }

    fun planAutonomousMove(
        profile: SpeciesBehaviorProfile,
        pose: PetPose,
        world: PetWorld,
        reason: BehaviorReason,
    ): MotionPlan? {
        return when (profile.species) {
            PetSpecies.PANDA -> planPandaWalk(pose, world, reason)
            PetSpecies.AFRICAN_SCOPS_OWL -> planOwlPerchShift(pose, world, reason)
            PetSpecies.UNKNOWN -> null
        }
    }

    fun planBreakApproach(
        profile: SpeciesBehaviorProfile,
        pose: PetPose,
        world: PetWorld,
    ): MotionPlan? {
        return when (profile.species) {
            PetSpecies.PANDA -> {
                val innerLeft = (world.screenWidth * 0.25f).roundToInt()
                val innerRight = (world.screenWidth * 0.68f).roundToInt()
                val targetX = pose.x.coerceIn(innerLeft, innerRight)
                val targetY = pandaFloorY(world)
                val preferred = if (targetX < pose.x) listOf("walk_left") else listOf("walk_right")
                MotionPlan(
                    fromX = pose.x,
                    fromY = pose.y,
                    targetX = targetX.coerceIn(0, world.screenWidth - pose.width),
                    targetY = targetY,
                    durationMs = 1_800L,
                    locomotionMode = LocomotionMode.HEAVY_WALK,
                    animationKey = chooseAvailableAnimation(preferred),
                    curve = MotionCurve.HEAVY_PANDA_STEP,
                    reason = BehaviorReason.PREPARE_BREAK,
                )
            }
            PetSpecies.AFRICAN_SCOPS_OWL -> {
                val nearUser = world.perchPoints.minByOrNull { point ->
                    abs(point.x - world.screenWidth / 2) + abs(point.y - pose.y)
                } ?: return null
                MotionPlan(
                    fromX = pose.x,
                    fromY = pose.y,
                    targetX = nearUser.x.coerceIn(0, world.screenWidth - pose.width),
                    targetY = nearUser.y.coerceIn(0, world.screenHeight - pose.height),
                    durationMs = 1_000L,
                    locomotionMode = LocomotionMode.PERCH_SHIFT,
                    animationKey = chooseAvailableAnimation(listOf("land", "look_left", "look_right")),
                    curve = MotionCurve.OWL_ARC,
                    reason = BehaviorReason.PREPARE_BREAK,
                )
            }
            PetSpecies.UNKNOWN -> null
        }
    }

    private fun planPandaWalk(
        pose: PetPose,
        world: PetWorld,
        reason: BehaviorReason,
    ): MotionPlan {
        val direction = if (random.nextBoolean()) -1 else 1
        val minDistance = (world.screenWidth * 0.08f).roundToInt().coerceAtLeast(world.petSizePx / 2)
        val maxDistance = (world.screenWidth * 0.22f).roundToInt().coerceAtLeast(minDistance)
        val distance = random.nextInt(minDistance, maxDistance + 1) * direction
        val targetX = (pose.x + distance).coerceIn(0, (world.screenWidth - pose.width).coerceAtLeast(0))
        val animation = if (targetX < pose.x) "walk_left" else "walk_right"
        return MotionPlan(
            fromX = pose.x,
            fromY = pose.y,
            targetX = targetX,
            targetY = pandaFloorY(world),
            durationMs = random.nextLong(1_200L, 2_601L),
            locomotionMode = LocomotionMode.HEAVY_WALK,
            animationKey = chooseAvailableAnimation(listOf(animation)),
            curve = MotionCurve.HEAVY_PANDA_STEP,
            reason = reason,
        )
    }

    private fun planOwlPerchShift(
        pose: PetPose,
        world: PetWorld,
        reason: BehaviorReason,
    ): MotionPlan? {
        val candidates = world.perchPoints
            .filter { abs(it.x - pose.x) > world.petSizePx / 2 || abs(it.y - pose.y) > world.petSizePx / 2 }
        val target = candidates.randomOrNull(random) ?: world.perchPoints.firstOrNull() ?: return null
        return MotionPlan(
            fromX = pose.x,
            fromY = pose.y,
            targetX = target.x.coerceIn(0, (world.screenWidth - pose.width).coerceAtLeast(0)),
            targetY = target.y.coerceIn(0, (world.screenHeight - pose.height).coerceAtLeast(0)),
            durationMs = random.nextLong(700L, 1_601L),
            locomotionMode = LocomotionMode.PERCH_SHIFT,
            animationKey = chooseAvailableAnimation(listOf("land", "look_left", "look_right")),
            curve = MotionCurve.OWL_ARC,
            reason = reason,
        )
    }

    private fun pandaFloorY(world: PetWorld): Int {
        return random.nextInt(
            world.floorBandTop,
            (world.floorBandBottom + 1).coerceAtLeast(world.floorBandTop + 1),
        ).coerceIn(0, (world.screenHeight - world.petSizePx).coerceAtLeast(0))
    }

    data class AutonomousGesture(
        val animationKey: String,
        val durationMs: Long,
        val behaviorId: String,
    )
}
