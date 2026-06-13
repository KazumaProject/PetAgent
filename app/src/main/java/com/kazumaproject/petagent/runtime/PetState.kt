package com.kazumaproject.petagent.runtime

import com.kazumaproject.petagent.agent.AgentEvent

data class PetState(
    val body: BodyState,
    val emotion: EmotionState,
    val need: NeedState,
    val agent: AgentState,
    val overlay: OverlayState,
    val interaction: InteractionState,
) {
    fun desiredAnimationKey(): String {
        if (interaction.isDragging) return "dragged"
        if (overlay.isMinimized || !overlay.isVisible) return "peek"

        when (agent) {
            AgentState.Thinking -> return "think"
            is AgentState.Speaking -> return "speak"
            is AgentState.Failed -> return body.transientAnimation ?: "confused"
            AgentState.Completed,
            AgentState.Idle,
            -> Unit
        }

        body.transientAnimation?.let { return it }
        if (need.isSleeping) return "sleep"
        return body.baseAnimation
    }

    companion object {
        fun initial(nowMs: Long): PetState {
            return PetState(
                body = BodyState(nextBlinkAtMs = nowMs + FIRST_BLINK_DELAY_MS),
                emotion = EmotionState(),
                need = NeedState(lastInteractionAtMs = nowMs),
                agent = AgentState.Idle,
                overlay = OverlayState(),
                interaction = InteractionState(),
            )
        }
    }
}

data class BodyState(
    val baseAnimation: String = "idle",
    val transientAnimation: String? = null,
    val transientExpiresAtMs: Long = 0L,
    val nextBlinkAtMs: Long = 0L,
)

data class EmotionState(
    val mood: PetMood = PetMood.Calm,
    val attention: PetAttention = PetAttention.Forward,
)

data class NeedState(
    val lastInteractionAtMs: Long,
    val isSleeping: Boolean = false,
    val sleepAfterMs: Long = SLEEP_AFTER_INACTIVITY_MS,
)

sealed interface AgentState {
    data object Idle : AgentState
    data object Thinking : AgentState
    data class Speaking(val text: String) : AgentState
    data object Completed : AgentState
    data class Failed(val message: String) : AgentState
}

data class OverlayState(
    val isVisible: Boolean = true,
    val isMinimized: Boolean = false,
)

data class InteractionState(
    val isPressed: Boolean = false,
    val isDragging: Boolean = false,
    val lastTapAtMs: Long = 0L,
)

enum class PetMood {
    Calm,
    Curious,
    Happy,
    Confused,
    Sleepy,
}

enum class PetAttention {
    Forward,
    Left,
    Right,
}

sealed interface PetAction {
    data class FrameTick(val nowMs: Long) : PetAction
    data class Tap(val nowMs: Long) : PetAction
    data class DragStarted(val nowMs: Long) : PetAction
    data class DragEnded(val nowMs: Long) : PetAction
    data class MinimizedChanged(val minimized: Boolean, val nowMs: Long) : PetAction
    data class AnimationFinished(val animationKey: String, val nowMs: Long) : PetAction
    data class AgentEventReceived(val event: AgentEvent, val nowMs: Long) : PetAction
}

const val FIRST_BLINK_DELAY_MS = 2_500L
const val BLINK_INTERVAL_MS = 6_500L
const val SLEEP_AFTER_INACTIVITY_MS = 30_000L
