package com.kazumaproject.petagent.motion

data class PetWorld(
    val screenWidth: Int,
    val screenHeight: Int,
    val petSizePx: Int,
    val floorZone: FloorZone,
    val perchPoints: List<WorldPoint>,
) {
    val floorBandTop: Int
        get() = floorZone.top

    val floorBandBottom: Int
        get() = floorZone.bottom
}

data class FloorZone(
    val top: Int,
    val bottom: Int,
    val preferredY: Int,
)

data class WorldPoint(
    val x: Int,
    val y: Int,
)
