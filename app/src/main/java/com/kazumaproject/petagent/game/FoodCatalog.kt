package com.kazumaproject.petagent.game

data class FoodItem(
    val id: String,
    val displayName: String,
    val iconText: String,
    val fullnessGain: Float,
    val happinessGain: Float,
    val affectionGain: Float,
    val experienceGain: Int,
    val favoriteSpecies: Set<String> = emptySet(),
)

object FoodCatalog {
    val bamboo = FoodItem(
        id = "bamboo",
        displayName = "Bamboo",
        iconText = "🎋",
        fullnessGain = 28f,
        happinessGain = 8f,
        affectionGain = 2f,
        experienceGain = 6,
        favoriteSpecies = setOf("panda"),
    )

    val apple = FoodItem(
        id = "apple",
        displayName = "Apple",
        iconText = "🍎",
        fullnessGain = 18f,
        happinessGain = 12f,
        affectionGain = 3f,
        experienceGain = 5,
    )

    val berry = FoodItem(
        id = "berry",
        displayName = "Berry",
        iconText = "🫐",
        fullnessGain = 14f,
        happinessGain = 10f,
        affectionGain = 2f,
        experienceGain = 5,
        favoriteSpecies = setOf("owl", "african_scops_owl"),
    )

    private val foodsById = listOf(bamboo, apple, berry).associateBy { it.id }

    fun byId(foodId: String): FoodItem {
        return foodsById[foodId] ?: apple
    }

    fun recommendedForSpecies(species: String): FoodItem {
        val normalizedSpecies = species.lowercase()
        return when {
            normalizedSpecies.contains("panda") -> bamboo
            normalizedSpecies.contains("owl") -> berry
            else -> apple
        }
    }
}
