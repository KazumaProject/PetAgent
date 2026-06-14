package com.kazumaproject.petagent.breakreminder

import java.util.Calendar

class BreakReminderEngine(
    private val repository: BreakReminderRepository,
) {
    suspend fun decide(
        settings: BreakReminderSettings,
        context: BreakReminderContext,
    ): BreakReminderDecision {
        val nowMs = context.nowWallClockMs
        val session = repository.loadOrCreateSession(nowMs)
        val canCountActiveTime = context.overlayVisible && !context.isMinimized && !context.isDragging
        val refreshedSession = if (canCountActiveTime) {
            val elapsedMs = (nowMs - session.lastActiveAtMs)
                .coerceIn(0L, MAX_ACTIVE_STEP_MS)
            session.copy(
                lastActiveAtMs = nowMs,
                accumulatedActiveMs = session.accumulatedActiveMs + elapsedMs,
                updatedAtMs = nowMs,
            )
        } else {
            session.copy(lastActiveAtMs = nowMs, updatedAtMs = nowMs)
        }
        repository.saveSession(refreshedSession)

        if (!settings.enabled || !canCountActiveTime || isQuietHour(settings, nowMs)) {
            return BreakReminderDecision.None
        }
        if (refreshedSession.snoozedUntilMs?.let { nowMs < it } == true) {
            return BreakReminderDecision.None
        }
        if (refreshedSession.dismissedUntilMs?.let { nowMs < it } == true) {
            return BreakReminderDecision.None
        }

        val intervalMs = settings.intervalMinutes * ONE_MINUTE_MS
        val remainingMs = intervalMs - refreshedSession.accumulatedActiveMs
        if (remainingMs in 1L..ALMOST_DUE_WINDOW_MS) {
            return BreakReminderDecision.AlmostDue(remainingMs = remainingMs)
        }

        if (remainingMs <= 0L) {
            val shownRecently = refreshedSession.lastReminderAtMs?.let {
                nowMs - it < REMINDER_REPEAT_GUARD_MS
            } == true
            if (shownRecently) return BreakReminderDecision.None

            repository.markReminderShown(
                petId = context.petId,
                settings = settings,
                session = refreshedSession,
                nowMs = nowMs,
            )
            return BreakReminderDecision.ShowReminder(
                message = messageFor(settings.tone),
                activeMinutes = (refreshedSession.accumulatedActiveMs / ONE_MINUTE_MS).toInt(),
                tone = settings.tone,
            )
        }

        return BreakReminderDecision.None
    }

    private fun isQuietHour(settings: BreakReminderSettings, nowMs: Long): Boolean {
        if (!settings.quietHoursEnabled) return false
        val hour = Calendar.getInstance().apply { timeInMillis = nowMs }
            .get(Calendar.HOUR_OF_DAY)
        val start = settings.quietStartHour
        val end = settings.quietEndHour
        return when {
            start == end -> true
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }

    private fun messageFor(tone: BreakReminderTone): String {
        return when (tone) {
            BreakReminderTone.SOFT -> "少しだけ休憩しませんか？"
            BreakReminderTone.NORMAL -> "そろそろ休憩しませんか？"
            BreakReminderTone.ASSERTIVE -> "休憩の時間です"
        }
    }

    private companion object {
        const val ONE_MINUTE_MS = 60_000L
        const val ALMOST_DUE_WINDOW_MS = 3 * ONE_MINUTE_MS
        const val MAX_ACTIVE_STEP_MS = 60_000L
        const val REMINDER_REPEAT_GUARD_MS = 5 * ONE_MINUTE_MS
    }
}
