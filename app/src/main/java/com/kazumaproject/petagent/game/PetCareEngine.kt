package com.kazumaproject.petagent.game

import kotlin.math.sqrt

object PetCareEngine {
    fun reduce(state: PetCareState, action: PetCareAction, species: String): CareResult {
        return when (action) {
            is PetCareAction.TimePassed -> {
                val decayedState = applyTimeDecay(state, action.nowWallClockMs)
                CareResult(
                    state = decayedState,
                    reaction = CareReaction(animationKey = "idle", message = "", mood = CareMood.Calm),
                )
            }
            is PetCareAction.GiveFood -> onGiveFood(state, action.foodId, action.nowWallClockMs, species)
            is PetCareAction.GiveWater -> onGiveWater(state, action.nowWallClockMs)
            is PetCareAction.Play -> onPlay(state, action.nowWallClockMs)
        }
    }

    private fun onGiveFood(
        state: PetCareState,
        foodId: String,
        nowWallClockMs: Long,
        species: String,
    ): CareResult {
        val decayedState = applyTimeDecay(state, nowWallClockMs)
        if (decayedState.fullness >= 95f) {
            return CareResult(
                state = decayedState.copy(happiness = decayedState.happiness - 3f).clamped(),
                reaction = CareReaction(
                    animationKey = "confused",
                    message = "もうお腹いっぱいみたいです",
                    mood = CareMood.Overfed,
                ),
            )
        }

        val food = FoodCatalog.byId(foodId)
        val favoriteMultiplier = if (food.isFavoriteFor(species)) 1.25f else 1f
        val nextExperience = decayedState.experience + food.experienceGain
        val nextState = decayedState.copy(
            fullness = decayedState.fullness + food.fullnessGain,
            happiness = decayedState.happiness + food.happinessGain * favoriteMultiplier,
            affection = decayedState.affection + food.affectionGain * favoriteMultiplier,
            experience = nextExperience,
            level = levelForExperience(nextExperience),
            lastFedAtWallClockMs = nowWallClockMs,
        ).clamped()

        return CareResult(
            state = nextState,
            reaction = CareReaction(
                animationKey = "eat",
                message = "${food.displayName}を食べてうれしそうです",
                mood = CareMood.Happy,
            ),
        )
    }

    private fun onGiveWater(state: PetCareState, nowWallClockMs: Long): CareResult {
        val decayedState = applyTimeDecay(state, nowWallClockMs)
        if (decayedState.hydration >= 95f) {
            return CareResult(
                state = decayedState,
                reaction = CareReaction(
                    animationKey = "confused",
                    message = "今は水はいらないみたいです",
                    mood = CareMood.Refused,
                ),
            )
        }

        val nextExperience = decayedState.experience + 4
        val nextState = decayedState.copy(
            hydration = decayedState.hydration + 30f,
            happiness = decayedState.happiness + 5f,
            affection = decayedState.affection + 1.5f,
            experience = nextExperience,
            level = levelForExperience(nextExperience),
            lastWateredAtWallClockMs = nowWallClockMs,
        ).clamped()

        return CareResult(
            state = nextState,
            reaction = CareReaction(
                animationKey = "drink",
                message = "水を飲んで落ち着いています",
                mood = CareMood.Calm,
            ),
        )
    }

    private fun onPlay(state: PetCareState, nowWallClockMs: Long): CareResult {
        val decayedState = applyTimeDecay(state, nowWallClockMs)
        if (decayedState.energy < 15f) {
            return CareResult(
                state = decayedState,
                reaction = CareReaction(
                    animationKey = "sleep",
                    message = "少し疲れているみたいです",
                    mood = CareMood.Sleepy,
                ),
            )
        }

        val nextExperience = decayedState.experience + 8
        val nextState = decayedState.copy(
            energy = decayedState.energy - 12f,
            happiness = decayedState.happiness + 14f,
            affection = decayedState.affection + 3f,
            experience = nextExperience,
            level = levelForExperience(nextExperience),
            lastPlayedAtWallClockMs = nowWallClockMs,
        ).clamped()

        return CareResult(
            state = nextState,
            reaction = CareReaction(
                animationKey = "happy",
                message = "楽しそうに遊んでいます",
                mood = CareMood.Happy,
            ),
        )
    }

    private fun applyTimeDecay(state: PetCareState, nowWallClockMs: Long): PetCareState {
        val elapsedMs = (nowWallClockMs - state.lastUpdatedAtWallClockMs).coerceAtLeast(0L)
        val hours = elapsedMs / 3_600_000f
        if (hours <= 0f) {
            return state.copy(lastUpdatedAtWallClockMs = nowWallClockMs).clamped()
        }

        val nextFullness = state.fullness - 5.0f * hours
        val nextHydration = state.hydration - 7.0f * hours
        val happinessDecayPerHour = when {
            nextFullness < 20f || nextHydration < 20f -> 8.0f
            nextFullness < 40f || nextHydration < 40f -> 4.0f
            else -> 1.0f
        }

        return state.copy(
            lastUpdatedAtWallClockMs = nowWallClockMs,
            fullness = nextFullness,
            hydration = nextHydration,
            energy = state.energy - 2.0f * hours,
            happiness = state.happiness - happinessDecayPerHour * hours,
        ).clamped()
    }

    private fun levelForExperience(experience: Int): Int {
        return 1 + sqrt(experience / 80.0).toInt()
    }

    private fun FoodItem.isFavoriteFor(species: String): Boolean {
        val normalizedSpecies = species.lowercase()
        if (normalizedSpecies.isBlank()) return false
        return favoriteSpecies.any { favorite ->
            val normalizedFavorite = favorite.lowercase()
            normalizedSpecies.contains(normalizedFavorite) || normalizedFavorite.contains(normalizedSpecies)
        }
    }
}
