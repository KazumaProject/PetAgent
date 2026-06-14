package com.kazumaproject.petagent.breakreminder

import android.content.Context
import com.kazumaproject.petagent.PetPreferences

class BreakReminderSettingsRepository(context: Context) {
    private val prefs = PetPreferences.prefs(context.applicationContext)

    fun load(): BreakReminderSettings {
        return BreakReminderSettings(
            enabled = prefs.getBoolean(
                PetPreferences.KEY_BREAK_REMINDER_ENABLED,
                PetPreferences.DEFAULT_BREAK_REMINDER_ENABLED,
            ),
            intervalMinutes = prefs.getInt(
                PetPreferences.KEY_BREAK_REMINDER_INTERVAL_MINUTES,
                PetPreferences.DEFAULT_BREAK_REMINDER_INTERVAL_MINUTES,
            ),
            snoozeMinutes = prefs.getInt(
                PetPreferences.KEY_BREAK_REMINDER_SNOOZE_MINUTES,
                PetPreferences.DEFAULT_BREAK_REMINDER_SNOOZE_MINUTES,
            ),
            quietHoursEnabled = prefs.getBoolean(
                PetPreferences.KEY_BREAK_REMINDER_QUIET_HOURS_ENABLED,
                PetPreferences.DEFAULT_BREAK_REMINDER_QUIET_HOURS_ENABLED,
            ),
            quietStartHour = prefs.getInt(
                PetPreferences.KEY_BREAK_REMINDER_QUIET_START_HOUR,
                PetPreferences.DEFAULT_BREAK_REMINDER_QUIET_START_HOUR,
            ),
            quietEndHour = prefs.getInt(
                PetPreferences.KEY_BREAK_REMINDER_QUIET_END_HOUR,
                PetPreferences.DEFAULT_BREAK_REMINDER_QUIET_END_HOUR,
            ),
            tone = BreakReminderTone.fromPreference(
                prefs.getString(
                    PetPreferences.KEY_BREAK_REMINDER_TONE,
                    PetPreferences.DEFAULT_BREAK_REMINDER_TONE,
                ),
            ),
        ).sanitized()
    }
}
