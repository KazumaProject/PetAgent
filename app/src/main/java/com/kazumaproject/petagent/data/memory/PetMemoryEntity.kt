package com.kazumaproject.petagent.data.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pet_memory")
data class PetMemoryEntity(
    @PrimaryKey val petId: String,
    val species: String,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long,
    val totalTapCount: Int,
    val totalReminderShownCount: Int,
    val totalBreakAcceptedCount: Int,
    val totalSnoozeCount: Int,
    val totalDismissCount: Int,
    val lastBreakAcceptedAtMs: Long?,
    val updatedAtMs: Long,
)
