package com.kazumaproject.petagent.motion

import com.kazumaproject.petagent.behavior.BehaviorIntent
import com.kazumaproject.petagent.behavior.BehaviorReason
import com.kazumaproject.petagent.petpack.FrameSize
import com.kazumaproject.petagent.petpack.NormalizedPoint
import com.kazumaproject.petagent.petpack.NormalizedRect
import com.kazumaproject.petagent.petpack.PetManifest
import com.kazumaproject.petagent.petpack.PetPack
import com.kazumaproject.petagent.petpack.SpriteAnimation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PetMotionDirectorTest {
    @Test
    fun pandaTargetY_isAlwaysInsideFloorBand() {
        val planner = PetMotionPlanner(
            petPack = testPack("panda", listOf("idle", "walk_right", "walk_left")),
            random = Random(4),
        )
        val world = testWorld(
            floorZone = FloorZone(top = 1_380, bottom = 1_560, preferredY = 1_520),
        )
        val pose = testPose(x = 420, y = 120, facing = Facing.RIGHT)

        repeat(100) {
            val target = planner.choosePandaFloorTarget(pose, world)
            assertTrue(target.y in world.floorZone.top..world.floorZone.bottom)
        }
    }

    @Test
    fun owlTarget_usesProvidedPerchCandidate() {
        val perches = listOf(
            WorldPoint(40, 120),
            WorldPoint(860, 140),
            WorldPoint(500, 360),
        )
        val planner = PetMotionPlanner(
            petPack = testPack("african_scops_owl", listOf("idle", "fly_right")),
            random = Random(7),
        )

        val target = planner.chooseOwlPerchTarget(
            pose = testPose(x = 100, y = 500, facing = Facing.RIGHT),
            world = testWorld(perchPoints = perches),
        )

        assertTrue(target in perches)
    }

    @Test
    fun pandaWanderSequence_usesExpectedPhaseOrder() {
        val pack = testPack(
            species = "panda",
            animationKeys = listOf(
                "idle",
                "idle_smooth",
                "turn_right",
                "prepare_walk_right",
                "lumber_right",
                "stop_right",
            ),
        )
        val director = PetMotionDirector(
            petPack = pack,
            planner = PetMotionPlanner(pack, Random(1)),
        )

        val sequence = director.buildSequence(
            intent = BehaviorIntent.Wander(BehaviorReason.AUTONOMOUS_WANDER, urgency = 0.5f),
            pose = testPose(x = 12, y = 1_520, facing = Facing.RIGHT),
            world = testWorld(),
            species = "panda",
        )

        assertEquals(
            listOf("turn_right", "prepare_walk_right", "lumber_right", "stop_right", "idle_smooth"),
            sequence.animationKeys(),
        )
        assertEquals(
            listOf(
                MotionPhase.Animation::class,
                MotionPhase.Animation::class,
                MotionPhase.Move::class,
                MotionPhase.Animation::class,
                MotionPhase.Settle::class,
            ),
            sequence.phases.map { it::class },
        )
    }

    @Test
    fun owlWanderSequence_usesExpectedPhaseOrder() {
        val pack = testPack(
            species = "african_scops_owl",
            animationKeys = listOf(
                "idle",
                "head_tilt_right",
                "takeoff_right",
                "glide_right",
                "landing_right",
                "settle",
                "perch_idle_loop",
            ),
        )
        val director = PetMotionDirector(
            petPack = pack,
            planner = PetMotionPlanner(pack, Random(2)),
        )

        val sequence = director.buildSequence(
            intent = BehaviorIntent.Wander(BehaviorReason.AUTONOMOUS_WANDER, urgency = 0.5f),
            pose = testPose(x = 40, y = 520, facing = Facing.RIGHT),
            world = testWorld(perchPoints = listOf(WorldPoint(880, 160))),
            species = "african_scops_owl",
        )

        assertEquals(
            listOf(
                "head_tilt_right",
                "takeoff_right",
                "glide_right",
                "landing_right",
                "settle",
                "perch_idle_loop",
            ),
            sequence.animationKeys(),
        )
        assertEquals(
            listOf(
                MotionPhase.Animation::class,
                MotionPhase.Animation::class,
                MotionPhase.Move::class,
                MotionPhase.Animation::class,
                MotionPhase.Animation::class,
                MotionPhase.Settle::class,
            ),
            sequence.phases.map { it::class },
        )
    }

    @Test
    fun pandaWanderSequence_missingOptionalKeysFallsBackToAvailableAnimations() {
        val pack = testPack(
            species = "panda",
            animationKeys = listOf("idle", "look_right", "walk_right"),
        )
        val director = PetMotionDirector(
            petPack = pack,
            planner = PetMotionPlanner(pack, Random(1)),
        )

        val sequence = director.buildSequence(
            intent = BehaviorIntent.Wander(BehaviorReason.AUTONOMOUS_WANDER, urgency = 0.5f),
            pose = testPose(x = 12, y = 1_520, facing = Facing.RIGHT),
            world = testWorld(),
            species = "panda",
        )

        assertEquals(
            listOf("look_right", "walk_right", "walk_right", "idle", "idle"),
            sequence.animationKeys(),
        )
        assertTrue(sequence.animationKeys().all(pack::hasAnimation))
    }

    private fun MotionSequence.animationKeys(): List<String> {
        return phases.map { phase ->
            when (phase) {
                is MotionPhase.Animation -> phase.animationKey
                is MotionPhase.Move -> phase.animationKey
                is MotionPhase.Settle -> phase.animationKey
            }
        }
    }

    private fun testPose(
        x: Int,
        y: Int,
        facing: Facing,
    ): PetPose {
        return PetPose(
            x = x,
            y = y,
            width = 128,
            height = 128,
            facing = facing,
            isMoving = false,
        )
    }

    private fun testWorld(
        floorZone: FloorZone = FloorZone(top = 1_360, bottom = 1_560, preferredY = 1_520),
        perchPoints: List<WorldPoint> = emptyList(),
    ): PetWorld {
        return PetWorld(
            screenWidth = 1_000,
            screenHeight = 1_800,
            petSizePx = 128,
            floorZone = floorZone,
            perchPoints = perchPoints,
        )
    }

    private fun testPack(
        species: String,
        animationKeys: List<String>,
        fallbacks: Map<String, String> = emptyMap(),
    ): PetPack {
        val animations = animationKeys.associateWith { key ->
            SpriteAnimation(
                key = key,
                type = "spritesheet",
                file = "sprites/$key.png",
                frameWidth = 256,
                frameHeight = 256,
                frameCount = 1,
                fps = 8,
                loop = true,
            )
        }
        return PetPack(
            basePath = "test_pack",
            manifest = PetManifest(
                formatVersion = 1,
                petId = "test_pet",
                displayName = "Test Pet",
                species = species,
                description = "",
                defaultSizeDp = 88,
                minSizeDp = 64,
                maxSizeDp = 160,
                anchor = NormalizedPoint(0.5f, 0.88f),
                hitbox = NormalizedRect(0f, 0f, 1f, 1f),
            ),
            frameSize = FrameSize(256, 256),
            animations = animations,
            fallbacks = fallbacks,
        )
    }
}
