package com.kazumaproject.petagent

import android.content.Context

object PetPreferences {
    const val PREFS_NAME = "pet_agent_settings"
    const val KEY_SELECTED_PET_ID = "selected_pet_id"
    const val KEY_SELECTED_PET_BASE_PATH = "selected_pet_base_path"
    const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    const val KEY_BREAK_REMINDER_ENABLED = "break_reminder_enabled"
    const val KEY_BREAK_REMINDER_INTERVAL_MINUTES = "break_reminder_interval_minutes"
    const val KEY_BREAK_REMINDER_SNOOZE_MINUTES = "break_reminder_snooze_minutes"
    const val KEY_BREAK_REMINDER_QUIET_HOURS_ENABLED = "break_reminder_quiet_hours_enabled"
    const val KEY_BREAK_REMINDER_QUIET_START_HOUR = "break_reminder_quiet_start_hour"
    const val KEY_BREAK_REMINDER_QUIET_END_HOUR = "break_reminder_quiet_end_hour"
    const val KEY_BREAK_REMINDER_TONE = "break_reminder_tone"
    const val KEY_AUTONOMOUS_BEHAVIOR_ENABLED = "autonomous_behavior_enabled"
    const val KEY_AUTONOMOUS_BEHAVIOR_FREQUENCY = "autonomous_behavior_frequency"
    const val KEY_AUTONOMOUS_MOVE_ENABLED = "autonomous_move_enabled"
    const val KEY_APPROACH_BEFORE_BREAK_ENABLED = "approach_before_break_enabled"
    const val DEFAULT_PET_ID = "default_african_scops_owl"
    const val DEFAULT_PET_BASE_PATH = "default_african_scops_owl"
    const val DEFAULT_BREAK_REMINDER_ENABLED = true
    const val DEFAULT_BREAK_REMINDER_INTERVAL_MINUTES = 25
    const val DEFAULT_BREAK_REMINDER_SNOOZE_MINUTES = 10
    const val DEFAULT_BREAK_REMINDER_QUIET_HOURS_ENABLED = true
    const val DEFAULT_BREAK_REMINDER_QUIET_START_HOUR = 22
    const val DEFAULT_BREAK_REMINDER_QUIET_END_HOUR = 7
    const val DEFAULT_BREAK_REMINDER_TONE = "normal"
    const val DEFAULT_AUTONOMOUS_BEHAVIOR_ENABLED = true
    const val DEFAULT_AUTONOMOUS_BEHAVIOR_FREQUENCY = "normal"
    const val DEFAULT_AUTONOMOUS_MOVE_ENABLED = true
    const val DEFAULT_APPROACH_BEFORE_BREAK_ENABLED = true

    fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun petSizeKey(petId: String) = "pet_size_dp_$petId"
}
