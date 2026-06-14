package com.kazumaproject.petagent.game

sealed interface PetCareAction {
    data class TimePassed(val nowWallClockMs: Long) : PetCareAction
    data class GiveFood(val foodId: String, val nowWallClockMs: Long) : PetCareAction
    data class GiveWater(val nowWallClockMs: Long) : PetCareAction
    data class Play(val nowWallClockMs: Long) : PetCareAction
}
