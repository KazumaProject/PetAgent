package com.kazumaproject.petagent.data.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PetMemoryRepository(
    private val dao: PetMemoryDao,
) {
    suspend fun ensureSeen(
        petId: String,
        species: String,
        nowMs: Long,
    ) = withContext(Dispatchers.IO) {
        val current = dao.get(petId)
        if (current == null) {
            dao.upsert(
                PetMemoryEntity(
                    petId = petId,
                    species = species,
                    firstSeenAtMs = nowMs,
                    lastSeenAtMs = nowMs,
                    totalTapCount = 0,
                    totalReminderShownCount = 0,
                    totalBreakAcceptedCount = 0,
                    totalSnoozeCount = 0,
                    totalDismissCount = 0,
                    lastBreakAcceptedAtMs = null,
                    updatedAtMs = nowMs,
                ),
            )
        } else {
            dao.upsert(current.copy(species = species, lastSeenAtMs = nowMs, updatedAtMs = nowMs))
        }
    }

    suspend fun incrementTap(petId: String, species: String, nowMs: Long) {
        update(petId, species, nowMs) {
            it.copy(totalTapCount = it.totalTapCount + 1)
        }
    }

    suspend fun incrementReminderShown(petId: String, species: String, nowMs: Long) {
        update(petId, species, nowMs) {
            it.copy(totalReminderShownCount = it.totalReminderShownCount + 1)
        }
    }

    suspend fun incrementBreakAccepted(petId: String, species: String, nowMs: Long) {
        update(petId, species, nowMs) {
            it.copy(
                totalBreakAcceptedCount = it.totalBreakAcceptedCount + 1,
                lastBreakAcceptedAtMs = nowMs,
            )
        }
    }

    suspend fun incrementSnooze(petId: String, species: String, nowMs: Long) {
        update(petId, species, nowMs) {
            it.copy(totalSnoozeCount = it.totalSnoozeCount + 1)
        }
    }

    suspend fun incrementDismiss(petId: String, species: String, nowMs: Long) {
        update(petId, species, nowMs) {
            it.copy(totalDismissCount = it.totalDismissCount + 1)
        }
    }

    private suspend fun update(
        petId: String,
        species: String,
        nowMs: Long,
        transform: (PetMemoryEntity) -> PetMemoryEntity,
    ) = withContext(Dispatchers.IO) {
        val current = dao.get(petId) ?: PetMemoryEntity(
            petId = petId,
            species = species,
            firstSeenAtMs = nowMs,
            lastSeenAtMs = nowMs,
            totalTapCount = 0,
            totalReminderShownCount = 0,
            totalBreakAcceptedCount = 0,
            totalSnoozeCount = 0,
            totalDismissCount = 0,
            lastBreakAcceptedAtMs = null,
            updatedAtMs = nowMs,
        )
        dao.upsert(
            transform(current).copy(
                species = species,
                lastSeenAtMs = nowMs,
                updatedAtMs = nowMs,
            ),
        )
    }
}
