package com.kazumaproject.petagent.data.breakreminder

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "break_sessions")
data class BreakSessionEntity(
    @PrimaryKey val id: Long = 1L,
    val startedAtMs: Long,
    val lastActiveAtMs: Long,
    val accumulatedActiveMs: Long,
    val lastReminderAtMs: Long?,
    val snoozedUntilMs: Long?,
    val dismissedUntilMs: Long?,
    val updatedAtMs: Long,
)
