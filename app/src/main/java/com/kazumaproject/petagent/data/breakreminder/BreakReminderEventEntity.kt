package com.kazumaproject.petagent.data.breakreminder

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "break_reminder_events")
data class BreakReminderEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val shownAtMs: Long,
    val triggerActiveMs: Long,
    val configuredIntervalMinutes: Int,
    val action: BreakReminderAction,
    val petId: String,
)
