package com.kazumaproject.petagent.breakreminder

data class BreakReminderSettings(
    val enabled: Boolean,
    val intervalMinutes: Int,
    val snoozeMinutes: Int,
    val quietHoursEnabled: Boolean,
    val quietStartHour: Int,
    val quietEndHour: Int,
    val tone: BreakReminderTone,
) {
    fun sanitized(): BreakReminderSettings {
        return copy(
            intervalMinutes = intervalMinutes.coerceIn(1, 240),
            snoozeMinutes = snoozeMinutes.coerceIn(1, 240),
            quietStartHour = quietStartHour.coerceIn(0, 23),
            quietEndHour = quietEndHour.coerceIn(0, 23),
        )
    }
}
