package com.kazumaproject.petagent.game

import kotlin.math.roundToInt

enum class PrimaryNeed {
    Food,
    Water,
    Play,
    Sleep,
}

data class PetCareUiState(
    val fullness: Int,
    val hydration: Int,
    val energy: Int,
    val happiness: Int,
    val affection: Int,
    val level: Int,
    val primaryNeed: PrimaryNeed?,
)

fun PetCareState.toUiState(): PetCareUiState {
    return PetCareUiState(
        fullness = fullness.roundToCareInt(),
        hydration = hydration.roundToCareInt(),
        energy = energy.roundToCareInt(),
        happiness = happiness.roundToCareInt(),
        affection = affection.roundToCareInt(),
        level = level,
        primaryNeed = when {
            hydration < 25f -> PrimaryNeed.Water
            fullness < 25f -> PrimaryNeed.Food
            energy < 20f -> PrimaryNeed.Sleep
            happiness < 30f -> PrimaryNeed.Play
            else -> null
        },
    )
}

private fun Float.roundToCareInt(): Int = roundToInt().coerceIn(0, 100)
