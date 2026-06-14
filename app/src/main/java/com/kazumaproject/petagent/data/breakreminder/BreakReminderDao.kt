package com.kazumaproject.petagent.data.breakreminder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BreakReminderDao {
    @Query("SELECT * FROM break_sessions WHERE id = 1 LIMIT 1")
    suspend fun getSession(): BreakSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: BreakSessionEntity)

    @Insert
    suspend fun insertEvent(event: BreakReminderEventEntity): Long

    @Query(
        """
        SELECT COUNT(*) FROM break_reminder_events
        WHERE petId = :petId AND action = :action AND shownAtMs >= :sinceMs
        """,
    )
    suspend fun countEventsSince(
        petId: String,
        action: BreakReminderAction,
        sinceMs: Long,
    ): Int
}
