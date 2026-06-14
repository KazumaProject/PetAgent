package com.kazumaproject.petagent.runtime

import com.kazumaproject.petagent.agent.AgentEvent

object PetReducer {
    fun reduce(state: PetState, action: PetAction): PetState {
        return when (action) {
            is PetAction.FrameTick -> onTick(state, action.nowMs)
            is PetAction.BrainTick -> onBrainTick(state, action.nowMs)
            is PetAction.Tap -> onTap(state, action.nowMs)
            is PetAction.DragStarted -> state.copy(
                interaction = state.interaction.copy(isPressed = true, isDragging = true),
                need = state.need.copy(lastInteractionAtMs = action.nowMs, isSleeping = false),
                emotion = state.emotion.copy(mood = PetMood.Curious),
                agent = AgentState.Idle,
            )
            is PetAction.DragEnded -> state.copy(
                interaction = state.interaction.copy(isPressed = false, isDragging = false),
                body = state.body.withTransient("land", action.nowMs, durationMs = 700L),
                need = state.need.copy(lastInteractionAtMs = action.nowMs, isSleeping = false),
            )
            is PetAction.MinimizedChanged -> state.copy(
                overlay = state.overlay.copy(isMinimized = action.minimized),
                body = state.body.clearTransient(),
                need = state.need.copy(lastInteractionAtMs = action.nowMs, isSleeping = false),
                agent = if (action.minimized) AgentState.Idle else state.agent,
            )
            is PetAction.AnimationFinished -> onAnimationFinished(state, action.animationKey, action.nowMs)
            is PetAction.AgentEventReceived -> onAgentEvent(state, action.event, action.nowMs)
            is PetAction.AutonomousAnimationStarted -> onAutonomousAnimationStarted(
                state = state,
                animationKey = action.animationKey,
                durationMs = action.durationMs,
                nowMs = action.nowMs,
            )
            is PetAction.AutonomousMoveStarted -> state.copy(
                need = state.need.copy(lastAutonomousMoveAtMs = action.nowMs, boredom = (state.need.boredom - 0.05f).coerceIn01()),
            )
            is PetAction.AutonomousMoveFinished -> state.copy(
                body = state.body.clearTransient(),
                emotion = state.emotion.copy(mood = PetMood.Calm),
            )
            is PetAction.BreakReminderShown -> state.copy(
                body = state.body.withTransient("look_left", action.nowMs, durationMs = 1_400L),
                need = state.need.copy(lastReminderProposalAtMs = action.nowMs, helpfulness = (state.need.helpfulness + 0.08f).coerceIn01()),
                emotion = state.emotion.copy(mood = PetMood.Curious, attention = PetAttention.Left),
                agent = AgentState.Idle,
            )
            is PetAction.BreakAccepted -> state.copy(
                body = state.body.withTransient("happy", action.nowMs, durationMs = 1_500L),
                need = state.need.copy(
                    lastInteractionAtMs = action.nowMs,
                    isSleeping = false,
                    energy = (state.need.energy + 0.2f).coerceIn01(),
                    calmness = (state.need.calmness + 0.18f).coerceIn01(),
                    boredom = (state.need.boredom - 0.1f).coerceIn01(),
                ),
                emotion = state.emotion.copy(mood = PetMood.Happy, attention = PetAttention.Forward),
            )
            is PetAction.BreakSnoozed -> state.copy(
                body = state.body.withTransient("look_right", action.nowMs, durationMs = 900L),
                need = state.need.copy(
                    lastInteractionAtMs = action.nowMs,
                    isSleeping = false,
                    calmness = (state.need.calmness + 0.05f).coerceIn01(),
                ),
                emotion = state.emotion.copy(mood = PetMood.Calm, attention = PetAttention.Right),
            )
            is PetAction.BreakDismissed -> state.copy(
                body = state.body.clearTransient(),
                need = state.need.copy(
                    lastInteractionAtMs = action.nowMs,
                    isSleeping = true,
                    calmness = (state.need.calmness + 0.02f).coerceIn01(),
                    helpfulness = (state.need.helpfulness - 0.04f).coerceIn01(),
                ),
                emotion = state.emotion.copy(mood = PetMood.Sleepy, attention = PetAttention.Forward),
            )
            is PetAction.ReminderProposalPrepared -> state.copy(
                need = state.need.copy(lastReminderProposalAtMs = action.nowMs),
            )
        }
    }

    private fun onTick(state: PetState, nowMs: Long): PetState {
        var next = state

        if (next.body.transientAnimation != null && next.body.transientExpiresAtMs in 1..nowMs) {
            val expiredAnimation = next.body.transientAnimation
            next = next.copy(body = next.body.clearTransient())
            next = when (expiredAnimation) {
                "blink" -> next.copy(body = next.body.copy(nextBlinkAtMs = nowMs + BLINK_INTERVAL_MS))
                "happy" -> if (next.agent == AgentState.Completed) {
                    next.copy(agent = AgentState.Idle, emotion = next.emotion.copy(mood = PetMood.Calm))
                } else {
                    next
                }
                "confused" -> if (next.agent is AgentState.Failed) {
                    next.copy(agent = AgentState.Idle, emotion = next.emotion.copy(mood = PetMood.Calm))
                } else {
                    next
                }
                else -> next
            }
        }

        val agentIsActive = next.agent is AgentState.Thinking || next.agent is AgentState.Speaking
        val canSleep = !agentIsActive && !next.interaction.isDragging && !next.overlay.isMinimized
        if (canSleep && nowMs - next.need.lastInteractionAtMs >= next.need.sleepAfterMs) {
            next = next.copy(
                need = next.need.copy(isSleeping = true),
                emotion = next.emotion.copy(mood = PetMood.Sleepy),
                body = next.body.clearTransient(),
            )
        }

        val canBlink = !next.need.isSleeping &&
            !agentIsActive &&
            !next.interaction.isDragging &&
            !next.overlay.isMinimized &&
            next.body.transientAnimation == null

        if (canBlink && nowMs >= next.body.nextBlinkAtMs) {
            next = next.copy(
                body = next.body.withTransient("blink", nowMs, durationMs = 900L),
            )
        }

        return next
    }

    private fun onBrainTick(state: PetState, nowMs: Long): PetState {
        val next = onTick(state, nowMs)
        if (next.need.isSleeping || next.interaction.isDragging || next.overlay.isMinimized) {
            return next
        }
        return next.copy(
            need = next.need.copy(
                boredom = (next.need.boredom + 0.01f).coerceIn01(),
                curiosity = (next.need.curiosity + 0.005f).coerceIn01(),
                energy = (next.need.energy - 0.004f).coerceIn01(),
            ),
        )
    }

    private fun onTap(state: PetState, nowMs: Long): PetState {
        val attention = if ((nowMs / 1_000L) % 2L == 0L) PetAttention.Left else PetAttention.Right
        val lookAnimation = if (state.need.isSleeping) {
            "wake_up"
        } else if (attention == PetAttention.Left) {
            "look_left"
        } else {
            "look_right"
        }
        return state.copy(
            body = state.body.withTransient(lookAnimation, nowMs, durationMs = if (lookAnimation == "wake_up") 900L else 650L),
            emotion = state.emotion.copy(mood = PetMood.Curious, attention = attention),
            need = state.need.copy(
                lastInteractionAtMs = nowMs,
                isSleeping = false,
                boredom = (state.need.boredom - 0.2f).coerceIn01(),
                attention = (state.need.attention + 0.18f).coerceIn01(),
                curiosity = (state.need.curiosity + 0.12f).coerceIn01(),
            ),
            interaction = state.interaction.copy(isPressed = false, lastTapAtMs = nowMs),
        )
    }

    private fun onAnimationFinished(state: PetState, animationKey: String, nowMs: Long): PetState {
        var next = state
        if (next.body.transientAnimation == animationKey) {
            next = next.copy(body = next.body.clearTransient())
        }

        next = when (animationKey) {
            "blink" -> next.copy(body = next.body.copy(nextBlinkAtMs = nowMs + BLINK_INTERVAL_MS))
            "happy" -> if (next.agent == AgentState.Completed) {
                next.copy(agent = AgentState.Idle, emotion = next.emotion.copy(mood = PetMood.Calm))
            } else {
                next
            }
            "confused" -> if (next.agent is AgentState.Failed) {
                next.copy(agent = AgentState.Idle, emotion = next.emotion.copy(mood = PetMood.Calm))
            } else {
                next
            }
            "land" -> next.copy(emotion = next.emotion.copy(mood = PetMood.Calm))
            else -> next
        }
        return next
    }

    private fun onAgentEvent(state: PetState, event: AgentEvent, nowMs: Long): PetState {
        return when (event) {
            AgentEvent.ThinkingStarted -> state.copy(
                agent = AgentState.Thinking,
                body = state.body.clearTransient(),
                need = state.need.copy(lastInteractionAtMs = nowMs, isSleeping = false),
                emotion = state.emotion.copy(mood = PetMood.Curious, attention = PetAttention.Forward),
            )
            is AgentEvent.TokenReceived -> {
                val currentText = (state.agent as? AgentState.Speaking)?.text.orEmpty()
                state.copy(
                    agent = AgentState.Speaking(currentText + event.token),
                    need = state.need.copy(lastInteractionAtMs = nowMs, isSleeping = false),
                )
            }
            is AgentEvent.ResponseCompleted -> state.copy(
                agent = AgentState.Completed,
                body = state.body.withTransient("happy", nowMs, durationMs = 1_300L),
                need = state.need.copy(lastInteractionAtMs = nowMs, isSleeping = false),
                emotion = state.emotion.copy(mood = PetMood.Happy, attention = PetAttention.Forward),
            )
            is AgentEvent.Error -> state.copy(
                agent = AgentState.Failed(event.message),
                body = state.body.withTransient("confused", nowMs, durationMs = 1_200L),
                need = state.need.copy(lastInteractionAtMs = nowMs, isSleeping = false),
                emotion = state.emotion.copy(mood = PetMood.Confused, attention = PetAttention.Forward),
            )
        }
    }

    private fun onAutonomousAnimationStarted(
        state: PetState,
        animationKey: String,
        durationMs: Long,
        nowMs: Long,
    ): PetState {
        val mood = when (animationKey) {
            "happy" -> PetMood.Happy
            "confused" -> PetMood.Confused
            "sleep" -> PetMood.Sleepy
            "sit",
            "look_left",
            "look_right",
            "think",
            -> PetMood.Calm
            else -> state.emotion.mood
        }

        return state.copy(
            body = state.body.withTransient(animationKey, nowMs, durationMs),
            emotion = state.emotion.copy(mood = mood, attention = PetAttention.Forward),
            need = state.need.copy(
                lastAutonomousGestureAtMs = nowMs,
                isSleeping = animationKey == "sleep",
                boredom = (state.need.boredom - 0.05f).coerceIn01(),
                calmness = (state.need.calmness + 0.03f).coerceIn01(),
            ),
            agent = AgentState.Idle,
            interaction = state.interaction.copy(isPressed = false),
        )
    }

    private fun BodyState.withTransient(animation: String, nowMs: Long, durationMs: Long): BodyState {
        return copy(
            transientAnimation = animation,
            transientExpiresAtMs = nowMs + durationMs,
        )
    }

    private fun BodyState.clearTransient(): BodyState {
        return copy(transientAnimation = null, transientExpiresAtMs = 0L)
    }

    private fun Float.coerceIn01(): Float = coerceIn(0f, 1f)
}
