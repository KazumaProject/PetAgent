package com.kazumaproject.petagent.petpack

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.IOException

class PetPackLoader(
    private val context: Context,
    private val basePath: String = "default_african_scops_owl",
) {
    fun load(): PetPack {
        val manifestJson = JSONObject(readAssetText("$basePath/manifest.json"))
        val animationsJson = JSONObject(readAssetText("$basePath/animations.json"))
        val manifest = parseManifest(manifestJson)
        val frameSize = animationsJson.optJSONObject("frameSize")?.let {
            FrameSize(
                width = it.optInt("width", 1).coerceAtLeast(1),
                height = it.optInt("height", 1).coerceAtLeast(1),
            )
        } ?: FrameSize(width = 1, height = 1)

        val parsedAnimations = linkedMapOf<String, SpriteAnimation>()
        val animationContainer = animationsJson.optJSONObject("animations") ?: JSONObject()
        val keys = animationContainer.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val animationJson = animationContainer.optJSONObject(key) ?: continue
            val animation = parseAnimation(key, animationJson) ?: continue
            val assetPath = "$basePath/${animation.file}"
            if (assetExists(assetPath)) {
                parsedAnimations[key] = animation
            } else {
                Log.w(TAG, "Skipping animation '$key'; missing asset: $assetPath")
            }
        }

        if (!parsedAnimations.containsKey("idle")) {
            Log.w(TAG, "PetPack has no valid idle animation; renderer will use the first valid animation if available.")
        }

        return PetPack(
            basePath = basePath,
            manifest = manifest,
            frameSize = frameSize,
            animations = parsedAnimations,
            fallbacks = parseStringMap(animationsJson.optJSONObject("fallbacks")),
        )
    }

    private fun parseManifest(json: JSONObject): PetManifest {
        val anchor = json.optJSONObject("anchor")
        val hitbox = json.optJSONObject("hitbox")
        return PetManifest(
            formatVersion = json.optInt("formatVersion", 1),
            petId = json.optString("petId", "default_pet"),
            displayName = json.optString("displayName", "Pet"),
            species = json.optString("species", "unknown"),
            description = json.optString("description", ""),
            defaultSizeDp = json.optInt("defaultSizeDp", 72).coerceIn(40, 240),
            minSizeDp = json.optInt("minSizeDp", 56).coerceIn(24, 240),
            maxSizeDp = json.optInt("maxSizeDp", 128).coerceIn(40, 320),
            anchor = NormalizedPoint(
                x = anchor?.optDouble("x", 0.5)?.toFloat() ?: 0.5f,
                y = anchor?.optDouble("y", 0.88)?.toFloat() ?: 0.88f,
            ),
            hitbox = NormalizedRect(
                x = hitbox?.optDouble("x", 0.0)?.toFloat() ?: 0f,
                y = hitbox?.optDouble("y", 0.0)?.toFloat() ?: 0f,
                width = hitbox?.optDouble("width", 1.0)?.toFloat() ?: 1f,
                height = hitbox?.optDouble("height", 1.0)?.toFloat() ?: 1f,
            ),
        )
    }

    private fun parseAnimation(key: String, json: JSONObject): SpriteAnimation? {
        val type = json.optString("type", "spritesheet")
        val file = json.optString("file")
        val frameWidth = json.optInt("frameWidth", 0)
        val frameHeight = json.optInt("frameHeight", 0)
        val frameCount = json.optInt("frameCount", 0)
        val fps = json.optInt("fps", 0)
        if (type != "spritesheet" || file.isBlank() || frameWidth <= 0 || frameHeight <= 0 || frameCount <= 0 || fps <= 0) {
            Log.w(TAG, "Skipping invalid animation '$key'.")
            return null
        }

        return SpriteAnimation(
            key = key,
            type = type,
            file = file,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            frameCount = frameCount,
            fps = fps,
            loop = json.optBoolean("loop", true),
        )
    }

    private fun parseStringMap(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        val values = mutableMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optString(key)
            if (value.isNotBlank()) {
                values[key] = value
            }
        }
        return values
    }

    private fun readAssetText(path: String): String {
        return context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun assetExists(path: String): Boolean {
        return try {
            context.assets.open(path).use { true }
        } catch (_: IOException) {
            false
        }
    }

    private companion object {
        const val TAG = "PetPackLoader"
    }
}
