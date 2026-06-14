package com.kazumaproject.petagent.data

import androidx.room.TypeConverter
import com.kazumaproject.petagent.data.breakreminder.BreakReminderAction

class PetAgentTypeConverters {
    @TypeConverter
    fun fromBreakReminderAction(action: BreakReminderAction): String = action.name

    @TypeConverter
    fun toBreakReminderAction(value: String): BreakReminderAction {
        return runCatching { BreakReminderAction.valueOf(value) }
            .getOrDefault(BreakReminderAction.SHOWN)
    }
}
