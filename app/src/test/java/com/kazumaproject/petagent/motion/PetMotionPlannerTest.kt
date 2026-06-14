package com.kazumaproject.petagent.motion

import com.kazumaproject.petagent.petpack.FrameSize
import com.kazumaproject.petagent.petpack.NormalizedPoint
import com.kazumaproject.petagent.petpack.NormalizedRect
import com.kazumaproject.petagent.petpack.PetManifest
import com.kazumaproject.petagent.petpack.PetPack
import com.kazumaproject.petagent.petpack.SpriteAnimation
import org.junit.Assert.assertEquals
import org.junit.Test

class PetMotionPlannerTest {
    @Test
    fun chooseAvailableAnimation_prefersPandaLumberWhenPresent() {
        val planner = PetMotionPlanner(
            petPack = testPack(
                "idle",
                "walk_left",
                "lumber_left",
            ),
        )

        assertEquals(
            "lumber_left",
            planner.chooseAvailableAnimation(listOf("lumber_left", "walk_left")),
        )
    }

    @Test
    fun chooseAvailableAnimation_fallsBackToPandaWalkWhenLumberMissing() {
        val planner = PetMotionPlanner(
            petPack = testPack(
                "idle",
                "walk_left",
            ),
        )

        assertEquals(
            "walk_left",
            planner.chooseAvailableAnimation(listOf("lumber_left", "walk_left")),
        )
    }

    @Test
    fun chooseAvailableAnimation_fallsBackToOwlLandWhenFlightMissing() {
        val planner = PetMotionPlanner(
            petPack = testPack(
                "idle",
                "land",
                "look_left",
            ),
        )

        assertEquals(
            "land",
            planner.chooseAvailableAnimation(listOf("fly_left", "land", "look_left")),
        )
    }

    private fun testPack(vararg animationKeys: String): PetPack {
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
                species = "test",
                description = "",
                defaultSizeDp = 88,
                minSizeDp = 64,
                maxSizeDp = 160,
                anchor = NormalizedPoint(0.5f, 0.88f),
                hitbox = NormalizedRect(0f, 0f, 1f, 1f),
            ),
            frameSize = FrameSize(256, 256),
            animations = animations,
            fallbacks = emptyMap(),
        )
    }
}
