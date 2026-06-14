package com.kazumaproject.petagent.behavior

import com.kazumaproject.petagent.motion.PetWorld

data class PetContext(
    val petId: String,
    val speciesName: String,
    val world: PetWorld,
    val behaviorSettings: PetBehaviorSettings,
    val overlayVisible: Boolean,
    val isMinimized: Boolean,
    val isDragging: Boolean,
    val hasTransientAnimation: Boolean,
    val nowWallClockMs: Long,
)
