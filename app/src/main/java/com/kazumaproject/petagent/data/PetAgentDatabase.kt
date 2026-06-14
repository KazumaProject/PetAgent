package com.kazumaproject.petagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.kazumaproject.petagent.data.behavior.PetBehaviorDao
import com.kazumaproject.petagent.data.behavior.PetBehaviorEventEntity
import com.kazumaproject.petagent.data.breakreminder.BreakReminderDao
import com.kazumaproject.petagent.data.breakreminder.BreakReminderEventEntity
import com.kazumaproject.petagent.data.breakreminder.BreakSessionEntity
import com.kazumaproject.petagent.data.memory.PetMemoryDao
import com.kazumaproject.petagent.data.memory.PetMemoryEntity

@Database(
    entities = [
        BreakSessionEntity::class,
        BreakReminderEventEntity::class,
        PetMemoryEntity::class,
        PetBehaviorEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(PetAgentTypeConverters::class)
abstract class PetAgentDatabase : RoomDatabase() {
    abstract fun breakReminderDao(): BreakReminderDao
    abstract fun petMemoryDao(): PetMemoryDao
    abstract fun petBehaviorDao(): PetBehaviorDao

    companion object {
        @Volatile
        private var instance: PetAgentDatabase? = null

        fun getInstance(context: Context): PetAgentDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PetAgentDatabase::class.java,
                    "pet_agent.db",
                ).build().also { instance = it }
            }
        }
    }
}
