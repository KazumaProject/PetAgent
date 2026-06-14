package com.kazumaproject.petagent.game

data class PetCareState(
    val petId: String,
    val lastUpdatedAtWallClockMs: Long,
    val fullness: Float,
    val hydration: Float,
    val energy: Float,
    val happiness: Float,
    val affection: Float,
    val level: Int,
    val experience: Int,
    val lastFedAtWallClockMs: Long,
    val lastWateredAtWallClockMs: Long,
    val lastPlayedAtWallClockMs: Long,
) {
    fun clamped(): PetCareState {
        return copy(
            fullness = fullness.clampCareValue(),
            hydration = hydration.clampCareValue(),
            energy = energy.clampCareValue(),
            happiness = happiness.clampCareValue(),
            affection = affection.clampCareValue(),
            level = level.coerceAtLeast(1),
            experience = experience.coerceAtLeast(0),
        )
    }

    companion object {
        fun initial(petId: String, nowWallClockMs: Long): PetCareState {
            return PetCareState(
                petId = petId,
                lastUpdatedAtWallClockMs = nowWallClockMs,
                fullness = 70f,
                hydration = 70f,
                energy = 80f,
                happiness = 70f,
                affection = 0f,
                level = 1,
                experience = 0,
                lastFedAtWallClockMs = 0L,
                lastWateredAtWallClockMs = 0L,
                lastPlayedAtWallClockMs = 0L,
            )
        }
    }
}

internal fun Float.clampCareValue(): Float = coerceIn(0f, 100f)
