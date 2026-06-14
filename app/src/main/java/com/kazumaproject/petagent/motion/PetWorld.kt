package com.kazumaproject.petagent.motion

import android.graphics.Point

data class PetWorld(
    val screenWidth: Int,
    val screenHeight: Int,
    val petSizePx: Int,
    val floorBandTop: Int,
    val floorBandBottom: Int,
    val perchPoints: List<Point>,
)
