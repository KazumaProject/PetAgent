package com.kazumaproject.petagent.breakreminder

data class BreakReminderContext(
    val petId: String,
    val overlayVisible: Boolean,
    val isMinimized: Boolean,
    val isDragging: Boolean,
    val nowWallClockMs: Long,
)
