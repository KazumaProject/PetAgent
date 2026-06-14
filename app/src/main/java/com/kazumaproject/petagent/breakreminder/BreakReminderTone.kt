package com.kazumaproject.petagent.breakreminder

enum class BreakReminderTone(val preferenceValue: String) {
    SOFT("soft"),
    NORMAL("normal"),
    ASSERTIVE("assertive");

    companion object {
        fun fromPreference(value: String?): BreakReminderTone {
            return entries.firstOrNull { it.preferenceValue == value } ?: NORMAL
        }
    }
}
