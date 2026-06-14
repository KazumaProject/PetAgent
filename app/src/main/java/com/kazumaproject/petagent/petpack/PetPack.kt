package com.kazumaproject.petagent.petpack

import android.util.Log

data class PetPack(
    val basePath: String,
    val manifest: PetManifest,
    val frameSize: FrameSize,
    val animations: Map<String, SpriteAnimation>,
    val fallbacks: Map<String, String>,
) {
    fun resolveAnimation(requestedKey: String): SpriteAnimation? {
        animations[requestedKey]?.let { return it }

        val candidateKeys = listOfNotNull(
            fallbacks[requestedKey],
            builtInFallback(requestedKey),
            IDLE_ANIMATION,
        ).distinct()

        candidateKeys.forEach { key ->
            animations[key]?.let { return it }
        }

        animations.values.firstOrNull()?.let { return it }

        Log.e(TAG, "Unable to resolve animation '$requestedKey'; pet '${manifest.petId}' has no available animations.")
        return null
    }

    private fun builtInFallback(requestedKey: String): String? {
        return when (requestedKey) {
            "talk" -> "speak"
            "question" -> "think"
            "error" -> "confused"
            "wake_up" -> "blink"
            "minimized" -> "peek"
            else -> null
        }
    }

    private companion object {
        const val TAG = "PetPack"
        const val IDLE_ANIMATION = "idle"
    }
}

data class PetManifest(
    val formatVersion: Int,
    val petId: String,
    val displayName: String,
    val species: String,
    val description: String,
    val defaultSizeDp: Int,
    val minSizeDp: Int,
    val maxSizeDp: Int,
    val anchor: NormalizedPoint,
    val hitbox: NormalizedRect,
    val behaviorProfile: PetManifestBehaviorProfile? = null,
)

data class PetManifestBehaviorProfile(
    val species: String,
    val movementPlane: String,
    val defaultLocomotion: String,
    val idleBehaviors: List<String>,
    val autonomousBehaviors: List<PetManifestAutonomousBehavior>,
    val breakPreparation: PetManifestBreakPreparation?,
)

data class PetManifestAutonomousBehavior(
    val id: String,
    val animation: String?,
    val animationLeft: String?,
    val animationRight: String?,
    val minIntervalMs: Long?,
    val maxDistanceScreenRatio: Float?,
)

data class PetManifestBreakPreparation(
    val approachMode: String,
    val leadTimeMinutes: Int,
)

data class FrameSize(
    val width: Int,
    val height: Int,
)

data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

data class NormalizedRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

data class SpriteAnimation(
    val key: String,
    val type: String,
    val file: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val frameCount: Int,
    val fps: Int,
    val loop: Boolean,
)
