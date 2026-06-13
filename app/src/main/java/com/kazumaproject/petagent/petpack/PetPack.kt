package com.kazumaproject.petagent.petpack

data class PetPack(
    val basePath: String,
    val manifest: PetManifest,
    val frameSize: FrameSize,
    val animations: Map<String, SpriteAnimation>,
    val fallbacks: Map<String, String>,
) {
    fun resolveAnimation(requestedKey: String): SpriteAnimation? {
        animations[requestedKey]?.let { return it }

        val fallbackKey = fallbacks[requestedKey] ?: when (requestedKey) {
            "talk" -> "speak"
            "question" -> "think"
            "error" -> "confused"
            "wake_up" -> "blink"
            else -> "idle"
        }

        return animations[fallbackKey] ?: animations["idle"] ?: animations.values.firstOrNull()
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
