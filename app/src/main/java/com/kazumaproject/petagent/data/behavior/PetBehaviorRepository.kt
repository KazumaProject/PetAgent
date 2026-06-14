package com.kazumaproject.petagent.data.behavior

import com.kazumaproject.petagent.behavior.BehaviorReason
import com.kazumaproject.petagent.behavior.LocomotionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PetBehaviorRepository(
    private val dao: PetBehaviorDao,
) {
    suspend fun record(
        petId: String,
        species: String,
        occurredAtMs: Long,
        behaviorId: String,
        locomotionMode: LocomotionMode? = null,
        reason: BehaviorReason,
        fromX: Int? = null,
        fromY: Int? = null,
        toX: Int? = null,
        toY: Int? = null,
    ) = withContext(Dispatchers.IO) {
        dao.insert(
            PetBehaviorEventEntity(
                petId = petId,
                species = species,
                occurredAtMs = occurredAtMs,
                behaviorId = behaviorId,
                locomotionMode = locomotionMode?.name,
                reason = reason.name,
                fromX = fromX,
                fromY = fromY,
                toX = toX,
                toY = toY,
            ),
        )
    }
}
