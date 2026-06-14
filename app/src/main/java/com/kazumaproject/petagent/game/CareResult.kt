package com.kazumaproject.petagent.game

data class CareResult(
    val state: PetCareState,
    val reaction: CareReaction,
)

data class CareReaction(
    val animationKey: String,
    val message: String,
    val mood: CareMood,
)

enum class CareMood {
    Happy,
    Calm,
    Hungry,
    Thirsty,
    Sleepy,
    Overfed,
    Refused,
}
