package com.kazumaproject.petagent.data.behavior

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pet_behavior_events")
data class PetBehaviorEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val petId: String,
    val species: String,
    val occurredAtMs: Long,
    val behaviorId: String,
    val locomotionMode: String?,
    val reason: String,
    val fromX: Int?,
    val fromY: Int?,
    val toX: Int?,
    val toY: Int?,
)
