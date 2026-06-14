package com.kazumaproject.petagent.behavior

import com.kazumaproject.petagent.petpack.PetManifest

data class SpeciesBehaviorProfile(
    val species: PetSpecies,
    val movementPlane: MovementPlane,
    val defaultLocomotion: LocomotionMode,
    val idleAnimationKeys: List<String>,
    val autonomousAnimationKeys: List<String>,
    val preferredRestAnimationKey: String,
    val preferredSleepAnimationKey: String,
    val breakPrepareLeadTimeMinutes: Int,
) {
    companion object {
        fun fromManifest(
            manifest: PetManifest,
            availableAnimationKeys: Set<String>,
        ): SpeciesBehaviorProfile {
            val rawSpecies = manifest.behaviorProfile?.species?.ifBlank { null }
                ?: manifest.species
            val species = PetSpecies.from(rawSpecies)
            val movementPlane = manifest.behaviorProfile?.movementPlane
                ?.let { MovementPlane.from(it) }
                ?: defaultMovementPlane(species)
            val locomotion = manifest.behaviorProfile?.defaultLocomotion
                ?.let { LocomotionMode.from(it) }
                ?: defaultLocomotion(species)
            val defaultIdle = when (species) {
                PetSpecies.PANDA -> listOf("idle", "blink", "look_left", "look_right", "sit", "sleep")
                PetSpecies.AFRICAN_SCOPS_OWL -> listOf("idle", "blink", "look_left", "look_right", "think", "sleep")
                PetSpecies.UNKNOWN -> listOf("idle", "blink", "look_left", "look_right")
            }
            val idleKeys = (manifest.behaviorProfile?.idleBehaviors?.ifEmpty { null } ?: defaultIdle)
                .filter { availableAnimationKeys.contains(it) }
                .ifEmpty { listOf("idle") }
            val autonomousKeys = manifest.behaviorProfile?.autonomousBehaviors
                ?.flatMap { behavior ->
                    listOfNotNull(behavior.animation, behavior.animationLeft, behavior.animationRight)
                }
                ?.filter { availableAnimationKeys.contains(it) }
                ?.ifEmpty { null }
                ?: idleKeys

            return SpeciesBehaviorProfile(
                species = species,
                movementPlane = movementPlane,
                defaultLocomotion = locomotion,
                idleAnimationKeys = idleKeys,
                autonomousAnimationKeys = autonomousKeys,
                preferredRestAnimationKey = when {
                    availableAnimationKeys.contains("sit") -> "sit"
                    availableAnimationKeys.contains("think") -> "think"
                    else -> "idle"
                },
                preferredSleepAnimationKey = if (availableAnimationKeys.contains("sleep")) "sleep" else "idle",
                breakPrepareLeadTimeMinutes = manifest.behaviorProfile?.breakPreparation?.leadTimeMinutes
                    ?: 2,
            )
        }

        private fun defaultMovementPlane(species: PetSpecies): MovementPlane {
            return when (species) {
                PetSpecies.PANDA -> MovementPlane.FLOOR
                PetSpecies.AFRICAN_SCOPS_OWL -> MovementPlane.PERCH
                PetSpecies.UNKNOWN -> MovementPlane.FREE_SAFE
            }
        }

        private fun defaultLocomotion(species: PetSpecies): LocomotionMode {
            return when (species) {
                PetSpecies.PANDA -> LocomotionMode.HEAVY_WALK
                PetSpecies.AFRICAN_SCOPS_OWL -> LocomotionMode.PERCH_SHIFT
                PetSpecies.UNKNOWN -> LocomotionMode.NONE
            }
        }
    }
}

enum class PetSpecies {
    PANDA,
    AFRICAN_SCOPS_OWL,
    UNKNOWN;

    companion object {
        fun from(raw: String): PetSpecies {
            val value = raw.lowercase()
            return when {
                "panda" in value -> PANDA
                "african_scops_owl" in value || "owl" in value -> AFRICAN_SCOPS_OWL
                else -> UNKNOWN
            }
        }
    }
}

enum class MovementPlane {
    FLOOR,
    PERCH,
    FREE_SAFE;

    companion object {
        fun from(raw: String): MovementPlane {
            return when (raw.lowercase()) {
                "floor" -> FLOOR
                "perch" -> PERCH
                "free_safe" -> FREE_SAFE
                else -> FREE_SAFE
            }
        }
    }
}

enum class LocomotionMode {
    HEAVY_WALK,
    SHORT_STEP,
    HOP,
    SHORT_FLIGHT,
    PERCH_SHIFT,
    GLIDE,
    NONE;

    companion object {
        fun from(raw: String): LocomotionMode {
            return when (raw.lowercase()) {
                "heavy_walk" -> HEAVY_WALK
                "short_step" -> SHORT_STEP
                "hop" -> HOP
                "short_flight" -> SHORT_FLIGHT
                "perch_shift" -> PERCH_SHIFT
                "glide" -> GLIDE
                else -> NONE
            }
        }
    }
}
