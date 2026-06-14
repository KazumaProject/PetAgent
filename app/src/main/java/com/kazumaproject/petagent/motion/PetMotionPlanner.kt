package com.kazumaproject.petagent.motion

import com.kazumaproject.petagent.behavior.BehaviorReason
import com.kazumaproject.petagent.behavior.LocomotionMode
import com.kazumaproject.petagent.behavior.PetBehaviorFrequency
import com.kazumaproject.petagent.behavior.PetBehaviorSettings
import com.kazumaproject.petagent.behavior.PetSpecies
import com.kazumaproject.petagent.behavior.SpeciesBehaviorProfile
import com.kazumaproject.petagent.petpack.PetPack
import kotlin.math.abs
import kotlin.math.hypot
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
            PetSpecies.PANDA -> planPandaFreeWalk(pose, world, reason)
            PetSpecies.AFRICAN_SCOPS_OWL -> planOwlFreeFlight(pose, world, reason)
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
                val preferred = pandaWalkCandidates(pose, targetX)
                val travelPx = abs(targetX - pose.x).coerceAtLeast(1)
                MotionPlan(
                    fromX = pose.x,
                    fromY = pose.y,
                    targetX = targetX.coerceIn(0, (world.screenWidth - pose.width).coerceAtLeast(0)),
                    targetY = targetY,
                    durationMs = pandaWalkDurationMs(travelPx, world),
                    locomotionMode = LocomotionMode.HEAVY_WALK,
                    animationKey = chooseAvailableAnimation(preferred),
                    curve = MotionCurve.PANDA_QUADRUPED_LUMBER,
                    reason = BehaviorReason.PREPARE_BREAK,
                )
            }
            PetSpecies.AFRICAN_SCOPS_OWL -> {
                val nearUser = world.perchPoints.minByOrNull { point ->
                    abs(point.x - world.screenWidth / 2) + abs(point.y - pose.y)
                } ?: return null
                planOwlMoveTo(pose, world, nearUser.x, nearUser.y, BehaviorReason.PREPARE_BREAK)
            }
            PetSpecies.UNKNOWN -> null
        }
    }

    private fun planPandaFreeWalk(
        pose: PetPose,
        world: PetWorld,
        reason: BehaviorReason,
    ): MotionPlan {
        val maxX = (world.screenWidth - pose.width).coerceAtLeast(0)
        val maxY = (world.screenHeight - pose.height).coerceAtLeast(0)
        val nearLeftWall = pose.x < world.petSizePx
        val nearRightWall = pose.x > maxX - world.petSizePx
        val direction = when {
            nearLeftWall -> 1
            nearRightWall -> -1
            pose.facing == Facing.LEFT && random.nextFloat() < 0.68f -> -1
            pose.facing == Facing.RIGHT && random.nextFloat() < 0.68f -> 1
            else -> if (random.nextBoolean()) -1 else 1
        }
        val minDistance = (world.screenWidth * 0.12f).roundToInt().coerceAtLeast(world.petSizePx)
        val maxDistance = (world.screenWidth * 0.34f).roundToInt().coerceAtLeast(minDistance)
        val distance = random.nextInt(minDistance, maxDistance + 1) * direction
        val targetX = (pose.x + distance).coerceIn(0, maxX)
        val targetY = random.nextInt(0, maxY + 1)
        val travelPx = hypot(
            (targetX - pose.x).toFloat(),
            (targetY - pose.y).toFloat(),
        ).coerceAtLeast(1f)
        return MotionPlan(
            fromX = pose.x,
            fromY = pose.y,
            targetX = targetX,
            targetY = targetY,
            durationMs = pandaWalkDurationMs(travelPx, world),
            locomotionMode = LocomotionMode.HEAVY_WALK,
            animationKey = chooseAvailableAnimation(pandaWalkCandidates(pose, targetX)),
            curve = MotionCurve.PANDA_QUADRUPED_LUMBER,
            reason = reason,
        )
    }

    private fun planOwlFreeFlight(
        pose: PetPose,
        world: PetWorld,
        reason: BehaviorReason,
    ): MotionPlan? {
        val maxX = (world.screenWidth - pose.width).coerceAtLeast(0)
        val maxY = (world.screenHeight - pose.height).coerceAtLeast(0)
        if (maxX == 0 && maxY == 0) return null

        val targetX = random.nextInt(0, maxX + 1)
        val targetY = random.nextInt(0, maxY + 1)
        val tooClose = abs(targetX - pose.x) < world.petSizePx / 2 &&
            abs(targetY - pose.y) < world.petSizePx / 2
        return if (tooClose) {
            val nudgedX = (targetX + world.petSizePx).coerceIn(0, maxX)
            planOwlMoveTo(pose, world, nudgedX, targetY, reason)
        } else {
            planOwlMoveTo(pose, world, targetX, targetY, reason)
        }
    }

    private fun planOwlMoveTo(
        pose: PetPose,
        world: PetWorld,
        rawTargetX: Int,
        rawTargetY: Int,
        reason: BehaviorReason,
    ): MotionPlan {
        val targetX = rawTargetX.coerceIn(0, (world.screenWidth - pose.width).coerceAtLeast(0))
        val targetY = rawTargetY.coerceIn(0, (world.screenHeight - pose.height).coerceAtLeast(0))
        val dx = abs(targetX - pose.x)
        val dy = abs(targetY - pose.y)
        val shortHop = dx < world.petSizePx * 1.35f && dy < world.petSizePx
        val right = when {
            targetX > pose.x -> true
            targetX < pose.x -> false
            else -> pose.facing != Facing.LEFT
        }
        val animationCandidates = when {
            shortHop && right -> listOf("hop_right", "land", "look_right")
            shortHop -> listOf("hop_left", "land", "look_left")
            right -> listOf("fly_right", "land", "look_right")
            else -> listOf("fly_left", "land", "look_left")
        }
        val travelPx = hypot(dx.toFloat(), dy.toFloat()).coerceAtLeast(1f)
        val flightSpeedPxPerSecond = world.petSizePx * random.nextFloatIn(2.0f, 2.9f)
        val durationMs = if (shortHop) {
            random.nextLong(850L, 1_151L)
        } else {
            ((travelPx / flightSpeedPxPerSecond) * 1_000f)
                .roundToInt()
                .coerceIn(1_150, 2_450)
                .toLong()
        }
        return MotionPlan(
            fromX = pose.x,
            fromY = pose.y,
            targetX = targetX,
            targetY = targetY,
            durationMs = durationMs,
            locomotionMode = if (shortHop) LocomotionMode.HOP else LocomotionMode.SHORT_FLIGHT,
            animationKey = chooseAvailableAnimation(animationCandidates),
            curve = if (shortHop) MotionCurve.OWL_HOP else MotionCurve.OWL_TAKEOFF_GLIDE_LAND,
            reason = reason,
        )
    }

    private fun pandaWalkCandidates(pose: PetPose, targetX: Int): List<String> {
        return when {
            targetX < pose.x -> listOf("lumber_left", "walk_left")
            targetX > pose.x -> listOf("lumber_right", "walk_right")
            pose.facing == Facing.LEFT -> listOf("lumber_left", "walk_left")
            else -> listOf("lumber_right", "walk_right")
        }
    }

    private fun pandaWalkDurationMs(travelPx: Int, world: PetWorld): Long {
        val heavySpeedPxPerSecond = world.petSizePx * random.nextFloatIn(0.34f, 0.46f)
        return ((travelPx / heavySpeedPxPerSecond) * 1_000f)
            .roundToInt()
            .coerceIn(1_900, 9_500)
            .toLong()
    }

    private fun pandaWalkDurationMs(travelPx: Float, world: PetWorld): Long {
        val heavySpeedPxPerSecond = world.petSizePx * random.nextFloatIn(0.34f, 0.46f)
        return ((travelPx / heavySpeedPxPerSecond) * 1_000f)
            .roundToInt()
            .coerceIn(1_900, 9_500)
            .toLong()
    }

    private fun pandaFloorY(world: PetWorld): Int {
        return random.nextInt(
            world.floorBandTop,
            (world.floorBandBottom + 1).coerceAtLeast(world.floorBandTop + 1),
        ).coerceIn(0, (world.screenHeight - world.petSizePx).coerceAtLeast(0))
    }

    private fun Random.nextFloatIn(min: Float, max: Float): Float {
        return min + nextFloat() * (max - min)
    }

    data class AutonomousGesture(
        val animationKey: String,
        val durationMs: Long,
        val behaviorId: String,
    )
}
