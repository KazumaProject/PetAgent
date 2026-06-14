package com.kazumaproject.petagent.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetCareEngineTest {
    @Test
    fun timePassed_decaysCareValuesUsingWallClockElapsedHours() {
        val start = 1_000L
        val state = PetCareState.initial("pet", start)

        val result = PetCareEngine.reduce(
            state = state,
            action = PetCareAction.TimePassed(start + 3_600_000L),
            species = "panda",
        )

        assertEquals(65f, result.state.fullness, FLOAT_DELTA)
        assertEquals(63f, result.state.hydration, FLOAT_DELTA)
        assertEquals(78f, result.state.energy, FLOAT_DELTA)
        assertEquals(69f, result.state.happiness, FLOAT_DELTA)
    }

    @Test
    fun giveFood_usesFavoriteSpeciesBonusAndUpdatesLevel() {
        val now = 1_000L
        val state = PetCareState.initial("pet", now).copy(
            fullness = 40f,
            experience = 79,
            level = 1,
        )

        val result = PetCareEngine.reduce(
            state = state,
            action = PetCareAction.GiveFood("bamboo", now),
            species = "giant_panda",
        )

        assertEquals("eat", result.reaction.animationKey)
        assertEquals(68f, result.state.fullness, FLOAT_DELTA)
        assertEquals(80f, result.state.happiness, FLOAT_DELTA)
        assertEquals(2.5f, result.state.affection, FLOAT_DELTA)
        assertEquals(85, result.state.experience)
        assertEquals(2, result.state.level)
    }

    @Test
    fun giveFood_whenFullRefusesWithoutUpdatingLastFedAt() {
        val now = 1_000L
        val state = PetCareState.initial("pet", now).copy(
            fullness = 96f,
            lastFedAtWallClockMs = 123L,
        )

        val result = PetCareEngine.reduce(
            state = state,
            action = PetCareAction.GiveFood("apple", now),
            species = "panda",
        )

        assertEquals(CareMood.Overfed, result.reaction.mood)
        assertEquals("confused", result.reaction.animationKey)
        assertEquals(123L, result.state.lastFedAtWallClockMs)
        assertTrue(result.state.happiness < state.happiness)
    }

    @Test
    fun recommendedFood_usesContainsBasedSpeciesMatching() {
        assertEquals("bamboo", FoodCatalog.recommendedForSpecies("giant_panda").id)
        assertEquals("berry", FoodCatalog.recommendedForSpecies("african_scops_owl").id)
        assertEquals("apple", FoodCatalog.recommendedForSpecies("cat").id)
    }

    private companion object {
        const val FLOAT_DELTA = 0.001f
    }
}
