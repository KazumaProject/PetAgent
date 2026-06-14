package com.kazumaproject.petagent.runtime

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import com.kazumaproject.petagent.agent.AgentEvent
import com.kazumaproject.petagent.agent.StubAgentCore
import com.kazumaproject.petagent.game.PetCareAction
import com.kazumaproject.petagent.game.PetCareEngine
import com.kazumaproject.petagent.game.PetCareRepository
import com.kazumaproject.petagent.game.PetCareState
import com.kazumaproject.petagent.game.PetCareUiState
import com.kazumaproject.petagent.game.toUiState
import com.kazumaproject.petagent.overlay.PetSpriteView
import kotlin.math.min

class PetRuntime(
    private val petView: PetSpriteView,
    private val agentCore: StubAgentCore,
    private val petId: String,
    private val species: String,
    private val careRepository: PetCareRepository,
    private val onStateChanged: (PetState) -> Unit = {},
    private val onCareUiStateChanged: (PetCareUiState) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private var running = false
    private var lastStepAtMs = 0L
    private var currentRequestedAnimationKey: String? = null
    private var pendingAgentStart: Runnable? = null
    private var state = PetState.initial(SystemClock.uptimeMillis())
    private var careState: PetCareState? = null
    private var lastCareTickWallClockMs: Long = 0L

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            step(frameTimeNanos)
            choreographer.postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        initializeCareState()
        renderImmediately()
        choreographer.postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
        pendingAgentStart?.let { handler.removeCallbacks(it) }
        pendingAgentStart = null
        agentCore.unload()
    }

    fun onPetTapped() {
        val nowMs = SystemClock.uptimeMillis()
        dispatch(PetAction.Tap(nowMs))

        pendingAgentStart?.let { handler.removeCallbacks(it) }
        pendingAgentStart = Runnable {
            agentCore.submit(prompt = "nearby things") { event ->
                onAgentEvent(event)
            }
        }.also {
            handler.postDelayed(it, TAP_LOOK_REACTION_MS)
        }
    }

    fun onDragStarted() {
        pendingAgentStart?.let { handler.removeCallbacks(it) }
        agentCore.cancel()
        dispatch(PetAction.DragStarted(SystemClock.uptimeMillis()))
    }

    fun onDragEnded() {
        dispatch(PetAction.DragEnded(SystemClock.uptimeMillis()))
    }

    fun onMinimizedChanged(minimized: Boolean) {
        if (minimized) {
            pendingAgentStart?.let { handler.removeCallbacks(it) }
            agentCore.cancel()
        }
        dispatch(PetAction.MinimizedChanged(minimized, SystemClock.uptimeMillis()))
    }

    fun giveFood(foodId: String) {
        reduceCareAction(
            actionFactory = { nowWallClockMs -> PetCareAction.GiveFood(foodId, nowWallClockMs) },
            sendReaction = true,
        )
    }

    fun giveWater() {
        reduceCareAction(
            actionFactory = { nowWallClockMs -> PetCareAction.GiveWater(nowWallClockMs) },
            sendReaction = true,
        )
    }

    fun play() {
        reduceCareAction(
            actionFactory = { nowWallClockMs -> PetCareAction.Play(nowWallClockMs) },
            sendReaction = true,
        )
    }

    fun refreshCareState() {
        reduceCareAction(
            actionFactory = { nowWallClockMs -> PetCareAction.TimePassed(nowWallClockMs) },
            sendReaction = false,
        )
    }

    private fun onAgentEvent(event: AgentEvent) {
        dispatch(PetAction.AgentEventReceived(event, SystemClock.uptimeMillis()))
    }

    private fun step(frameTimeNanos: Long) {
        val nowMs = frameTimeNanos / 1_000_000L
        val requestedAnimation = state.desiredAnimationKey()
        val targetDelayMs = targetFrameDelayMs(requestedAnimation)
        if (lastStepAtMs != 0L && nowMs - lastStepAtMs < targetDelayMs) {
            return
        }

        lastStepAtMs = nowMs
        reduceOnly(PetAction.FrameTick(nowMs))
        applyAnimation(frameTimeNanos)
        val advance = petView.advance(frameTimeNanos)
        if (advance.finished && advance.animationKey != null) {
            reduceOnly(PetAction.AnimationFinished(advance.animationKey, nowMs))
            applyAnimation(frameTimeNanos)
            petView.advance(frameTimeNanos)
        }
        maybeRefreshCareState()
    }

    private fun initializeCareState() {
        val nowWallClockMs = System.currentTimeMillis()
        val loadedState = careRepository.load(petId, nowWallClockMs)
        val result = PetCareEngine.reduce(
            state = loadedState,
            action = PetCareAction.TimePassed(nowWallClockMs),
            species = species,
        )
        careRepository.save(result.state)
        careState = result.state
        lastCareTickWallClockMs = nowWallClockMs
        onCareUiStateChanged(result.state.toUiState())
    }

    private fun maybeRefreshCareState() {
        val nowWallClockMs = System.currentTimeMillis()
        if (lastCareTickWallClockMs != 0L && nowWallClockMs - lastCareTickWallClockMs < CARE_TICK_MS) {
            return
        }
        refreshCareState()
    }

    private fun reduceCareAction(
        actionFactory: (Long) -> PetCareAction,
        sendReaction: Boolean,
    ) {
        val nowWallClockMs = System.currentTimeMillis()
        val nowUptimeMs = SystemClock.uptimeMillis()
        val currentState = careState ?: careRepository.load(petId, nowWallClockMs)
        val result = PetCareEngine.reduce(
            state = currentState,
            action = actionFactory(nowWallClockMs),
            species = species,
        )
        careRepository.save(result.state)
        careState = result.state
        lastCareTickWallClockMs = nowWallClockMs

        if (sendReaction) {
            dispatch(PetAction.CareReactionReceived(result.reaction.animationKey, nowUptimeMs))
        }
        onCareUiStateChanged(result.state.toUiState())
    }

    private fun dispatch(action: PetAction) {
        reduceOnly(action)
        lastStepAtMs = 0L
        renderImmediately()
    }

    private fun reduceOnly(action: PetAction) {
        val nextState = PetReducer.reduce(state, action)
        if (nextState != state) {
            state = nextState
            onStateChanged(state)
        }
    }

    private fun renderImmediately() {
        val nowNanos = SystemClock.uptimeMillis() * 1_000_000L
        applyAnimation(nowNanos)
        petView.advance(nowNanos)
    }

    private fun applyAnimation(frameTimeNanos: Long) {
        val requestedAnimation = state.desiredAnimationKey()
        if (requestedAnimation == currentRequestedAnimationKey) return
        currentRequestedAnimationKey = requestedAnimation
        petView.setAnimation(requestedAnimation, frameTimeNanos)
    }

    private fun targetFrameDelayMs(animationKey: String): Long {
        val animationFps = petView.animationFps(animationKey).coerceAtLeast(1)
        val effectiveFps = when {
            !state.overlay.isVisible -> 1
            state.overlay.isMinimized -> min(animationFps, 4)
            state.need.isSleeping -> min(animationFps, 4)
            animationKey == "idle" -> min(animationFps, 8)
            else -> min(animationFps, 12)
        }.coerceAtLeast(1)

        return 1_000L / effectiveFps
    }

    private companion object {
        const val TAP_LOOK_REACTION_MS = 430L
        const val CARE_TICK_MS = 30_000L
    }
}
