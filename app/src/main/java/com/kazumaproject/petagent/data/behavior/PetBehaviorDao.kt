package com.kazumaproject.petagent.data.behavior

import androidx.room.Dao
import androidx.room.Insert

@Dao
interface PetBehaviorDao {
    @Insert
    suspend fun insert(event: PetBehaviorEventEntity): Long
}
