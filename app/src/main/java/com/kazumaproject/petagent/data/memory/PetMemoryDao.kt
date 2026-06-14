package com.kazumaproject.petagent.data.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PetMemoryDao {
    @Query("SELECT * FROM pet_memory WHERE petId = :petId LIMIT 1")
    suspend fun get(petId: String): PetMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: PetMemoryEntity)
}
