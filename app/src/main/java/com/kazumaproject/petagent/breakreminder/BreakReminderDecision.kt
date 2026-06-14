package com.kazumaproject.petagent.breakreminder

sealed interface BreakReminderDecision {
    data object None : BreakReminderDecision

    data class AlmostDue(
        val remainingMs: Long,
    ) : BreakReminderDecision

    data class ShowReminder(
        val message: String,
        val activeMinutes: Int,
        val tone: BreakReminderTone,
    ) : BreakReminderDecision
}
