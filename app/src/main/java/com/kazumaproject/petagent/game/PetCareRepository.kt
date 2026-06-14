package com.kazumaproject.petagent.game

import android.content.Context
import com.kazumaproject.petagent.PetPreferences

class PetCareRepository(context: Context) {
    private val prefs = PetPreferences.prefs(context.applicationContext)

    fun load(petId: String, nowWallClockMs: Long): PetCareState {
        val prefix = keyPrefix(petId)
        if (!prefs.contains("${prefix}_last_updated_wall_clock")) {
            return PetCareState.initial(petId, nowWallClockMs)
        }

        return PetCareState(
            petId = petId,
            lastUpdatedAtWallClockMs = prefs.getLong("${prefix}_last_updated_wall_clock", nowWallClockMs),
            fullness = prefs.getFloat("${prefix}_fullness", 70f),
            hydration = prefs.getFloat("${prefix}_hydration", 70f),
            energy = prefs.getFloat("${prefix}_energy", 80f),
            happiness = prefs.getFloat("${prefix}_happiness", 70f),
            affection = prefs.getFloat("${prefix}_affection", 0f),
            level = prefs.getInt("${prefix}_level", 1),
            experience = prefs.getInt("${prefix}_experience", 0),
            lastFedAtWallClockMs = prefs.getLong("${prefix}_last_fed_wall_clock", 0L),
            lastWateredAtWallClockMs = prefs.getLong("${prefix}_last_watered_wall_clock", 0L),
            lastPlayedAtWallClockMs = prefs.getLong("${prefix}_last_played_wall_clock", 0L),
        ).clamped()
    }

    fun save(state: PetCareState) {
        val clampedState = state.clamped()
        val prefix = keyPrefix(clampedState.petId)
        prefs.edit()
            .putLong("${prefix}_last_updated_wall_clock", clampedState.lastUpdatedAtWallClockMs)
            .putFloat("${prefix}_fullness", clampedState.fullness)
            .putFloat("${prefix}_hydration", clampedState.hydration)
            .putFloat("${prefix}_energy", clampedState.energy)
            .putFloat("${prefix}_happiness", clampedState.happiness)
            .putFloat("${prefix}_affection", clampedState.affection)
            .putInt("${prefix}_level", clampedState.level)
            .putInt("${prefix}_experience", clampedState.experience)
            .putLong("${prefix}_last_fed_wall_clock", clampedState.lastFedAtWallClockMs)
            .putLong("${prefix}_last_watered_wall_clock", clampedState.lastWateredAtWallClockMs)
            .putLong("${prefix}_last_played_wall_clock", clampedState.lastPlayedAtWallClockMs)
            .apply()
    }

    private fun keyPrefix(petId: String): String = "care_$petId"
}
