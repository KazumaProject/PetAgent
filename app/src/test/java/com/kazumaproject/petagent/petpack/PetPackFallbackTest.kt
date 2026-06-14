package com.kazumaproject.petagent.petpack

import org.junit.Assert.assertEquals
import org.junit.Test

class PetPackFallbackTest {
    @Test
    fun resolveAnimationKeyOrFallback_usesExactThenPackFallbackThenProvidedFallbackThenFirstAnimation() {
        val pack = testPack(
            animationKeys = listOf("idle", "look_left", "confused"),
            fallbacks = mapOf("turn_left" to "look_left"),
        )

        assertEquals("confused", pack.resolveAnimationKeyOrFallback("confused"))
        assertEquals("look_left", pack.resolveAnimationKeyOrFallback("turn_left"))
        assertEquals("idle", pack.resolveAnimationKeyOrFallback("missing_optional", fallback = "idle"))
        assertEquals("idle", pack.resolveAnimationKeyOrFallback("missing_optional", fallback = "missing_too"))
    }

    private fun testPack(
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
            fallbacks = fallbacks,
        )
    }
}
