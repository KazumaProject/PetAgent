package com.kazumaproject.petagent.breakreminder

data class BreakStatus(
    val remainingMs: Long,
    val intervalMinutes: Int,
    val activeMinutes: Int,
    val todayBreakCount: Int,
)
