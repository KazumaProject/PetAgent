package com.kazumaproject.petagent.breakreminder

import com.kazumaproject.petagent.data.breakreminder.BreakReminderAction
import com.kazumaproject.petagent.data.breakreminder.BreakReminderDao
import com.kazumaproject.petagent.data.breakreminder.BreakReminderEventEntity
import com.kazumaproject.petagent.data.breakreminder.BreakSessionEntity
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BreakReminderRepository(
    private val dao: BreakReminderDao,
) {
    suspend fun loadOrCreateSession(nowMs: Long): BreakSessionEntity = withContext(Dispatchers.IO) {
        dao.getSession() ?: BreakSessionEntity(
            startedAtMs = nowMs,
            lastActiveAtMs = nowMs,
            accumulatedActiveMs = 0L,
            lastReminderAtMs = null,
            snoozedUntilMs = null,
            dismissedUntilMs = null,
            updatedAtMs = nowMs,
        ).also { dao.upsertSession(it) }
    }

    suspend fun saveSession(session: BreakSessionEntity) = withContext(Dispatchers.IO) {
        dao.upsertSession(session)
    }

    suspend fun recordEvent(
        petId: String,
        action: BreakReminderAction,
        triggerActiveMs: Long,
        configuredIntervalMinutes: Int,
        nowMs: Long,
    ) = withContext(Dispatchers.IO) {
        dao.insertEvent(
            BreakReminderEventEntity(
                shownAtMs = nowMs,
                triggerActiveMs = triggerActiveMs,
                configuredIntervalMinutes = configuredIntervalMinutes,
                action = action,
                petId = petId,
            ),
        )
    }

    suspend fun markReminderShown(
        petId: String,
        settings: BreakReminderSettings,
        session: BreakSessionEntity,
        nowMs: Long,
    ) {
        saveSession(session.copy(lastReminderAtMs = nowMs, updatedAtMs = nowMs))
        recordEvent(
            petId = petId,
            action = BreakReminderAction.SHOWN,
            triggerActiveMs = session.accumulatedActiveMs,
            configuredIntervalMinutes = settings.intervalMinutes,
            nowMs = nowMs,
        )
    }

    suspend fun acceptBreak(
        petId: String,
        settings: BreakReminderSettings,
        nowMs: Long,
    ) {
        val session = loadOrCreateSession(nowMs)
        recordEvent(
            petId = petId,
            action = BreakReminderAction.ACCEPTED_BREAK,
            triggerActiveMs = session.accumulatedActiveMs,
            configuredIntervalMinutes = settings.intervalMinutes,
            nowMs = nowMs,
        )
        saveSession(
            BreakSessionEntity(
                startedAtMs = nowMs,
                lastActiveAtMs = nowMs,
                accumulatedActiveMs = 0L,
                lastReminderAtMs = null,
                snoozedUntilMs = null,
                dismissedUntilMs = null,
                updatedAtMs = nowMs,
            ),
        )
    }

    suspend fun snooze(
        petId: String,
        settings: BreakReminderSettings,
        nowMs: Long,
    ) {
        val session = loadOrCreateSession(nowMs)
        recordEvent(
            petId = petId,
            action = BreakReminderAction.SNOOZED,
            triggerActiveMs = session.accumulatedActiveMs,
            configuredIntervalMinutes = settings.intervalMinutes,
            nowMs = nowMs,
        )
        saveSession(
            session.copy(
                snoozedUntilMs = nowMs + settings.snoozeMinutes * ONE_MINUTE_MS,
                lastReminderAtMs = nowMs,
                updatedAtMs = nowMs,
            ),
        )
    }

    suspend fun dismissToday(
        petId: String,
        settings: BreakReminderSettings,
        nowMs: Long,
    ) {
        val session = loadOrCreateSession(nowMs)
        recordEvent(
            petId = petId,
            action = BreakReminderAction.DISMISSED_TODAY,
            triggerActiveMs = session.accumulatedActiveMs,
            configuredIntervalMinutes = settings.intervalMinutes,
            nowMs = nowMs,
        )
        saveSession(
            session.copy(
                dismissedUntilMs = endOfTodayMs(nowMs),
                lastReminderAtMs = nowMs,
                updatedAtMs = nowMs,
            ),
        )
    }

    suspend fun breakStatus(
        petId: String,
        settings: BreakReminderSettings,
        nowMs: Long,
    ): BreakStatus {
        val session = loadOrCreateSession(nowMs)
        val intervalMs = settings.intervalMinutes * ONE_MINUTE_MS
        val remainingMs = (intervalMs - session.accumulatedActiveMs).coerceAtLeast(0L)
        return BreakStatus(
            remainingMs = remainingMs,
            intervalMinutes = settings.intervalMinutes,
            activeMinutes = (session.accumulatedActiveMs / ONE_MINUTE_MS).toInt(),
            todayBreakCount = todayAcceptedCount(petId, nowMs),
        )
    }

    suspend fun todayAcceptedCount(petId: String, nowMs: Long): Int = withContext(Dispatchers.IO) {
        dao.countEventsSince(
            petId = petId,
            action = BreakReminderAction.ACCEPTED_BREAK,
            sinceMs = startOfTodayMs(nowMs),
        )
    }

    private fun startOfTodayMs(nowMs: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun endOfTodayMs(nowMs: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = startOfTodayMs(nowMs)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis - 1L
    }

    private companion object {
        const val ONE_MINUTE_MS = 60_000L
    }
}
