package com.kazumaproject.petagent.runtime

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import com.kazumaproject.petagent.behavior.BehaviorReason
import com.kazumaproject.petagent.behavior.PetBehaviorIds
import com.kazumaproject.petagent.behavior.PetBehaviorSettingsRepository
import com.kazumaproject.petagent.behavior.PetBrain
import com.kazumaproject.petagent.behavior.PetContext
import com.kazumaproject.petagent.behavior.PetDecision
import com.kazumaproject.petagent.behavior.PetSpecies
import com.kazumaproject.petagent.behavior.SpeciesBehaviorProfile
import com.kazumaproject.petagent.breakreminder.BreakReminderEngine
import com.kazumaproject.petagent.breakreminder.BreakReminderRepository
import com.kazumaproject.petagent.breakreminder.BreakReminderSettingsRepository
import com.kazumaproject.petagent.breakreminder.BreakReminderTone
import com.kazumaproject.petagent.breakreminder.BreakStatus
import com.kazumaproject.petagent.data.behavior.PetBehaviorRepository
import com.kazumaproject.petagent.data.memory.PetMemoryRepository
import com.kazumaproject.petagent.motion.MotionPlan
import com.kazumaproject.petagent.motion.PetMotionController
import com.kazumaproject.petagent.motion.PetMotionPlanner
import com.kazumaproject.petagent.overlay.PetSpriteView
import com.kazumaproject.petagent.petpack.PetPack
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PetRuntime(
    private val petView: PetSpriteView,
    private val petPack: PetPack,
    private val behaviorProfile: SpeciesBehaviorProfile,
    private val petBrain: PetBrain,
    private val motionPlanner: PetMotionPlanner,
    private val breakReminderEngine: BreakReminderEngine,
    private val breakReminderRepository: BreakReminderRepository,
    private val breakReminderSettingsRepository: BreakReminderSettingsRepository,
    private val petMemoryRepository: PetMemoryRepository,
    private val petBehaviorRepository: PetBehaviorRepository,
    private val petBehaviorSettingsRepository: PetBehaviorSettingsRepository,
    private val petMotionController: PetMotionController,
    private val coroutineScope: CoroutineScope,
    private val onStateChanged: (PetState) -> Unit = {},
    private val onUiRequest: (PetUiRequest) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()
    private val petId = petPack.manifest.petId
    private val speciesName = petPack.manifest.species
    private var running = false
    private var brainThinking = false
    private var lastStepAtMs = 0L
    private var currentRequestedAnimationKey: String? = null
    private var state = PetState.initial(SystemClock.uptimeMillis())

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            step(frameTimeNanos)
            choreographer.postFrameCallback(this)
        }
    }

    private val brainTickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            runBrainTick()
            handler.postDelayed(this, BRAIN_TICK_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        val nowMs = System.currentTimeMillis()
        coroutineScope.launch {
            petMemoryRepository.ensureSeen(petId, speciesName, nowMs)
        }
        renderImmediately()
        choreographer.postFrameCallback(frameCallback)
        handler.postDelayed(brainTickRunnable, FIRST_BRAIN_TICK_DELAY_MS)
    }

    fun stop() {
        running = false
        choreographer.removeFrameCallback(frameCallback)
        handler.removeCallbacks(brainTickRunnable)
        petMotionController.cancelAutonomousMotion()
    }

    fun onPetTapped() {
        val nowMs = SystemClock.uptimeMillis()
        val nowWallClockMs = System.currentTimeMillis()
        dispatch(PetAction.Tap(nowMs))
        coroutineScope.launch {
            petMemoryRepository.incrementTap(petId, speciesName, nowWallClockMs)
            petBehaviorRepository.record(
                petId = petId,
                species = speciesName,
                occurredAtMs = nowWallClockMs,
                behaviorId = PetBehaviorIds.USER_TAPPED,
                reason = BehaviorReason.USER_TAPPED,
                fromX = petMotionController.currentPose().x,
                fromY = petMotionController.currentPose().y,
            )
        }
        onUiRequest(PetUiRequest.OpenBreakStatus)
    }

    fun onDragStarted() {
        petMotionController.cancelAutonomousMotion()
        dispatch(PetAction.DragStarted(SystemClock.uptimeMillis()))
    }

    fun onDragEnded() {
        dispatch(PetAction.DragEnded(SystemClock.uptimeMillis()))
    }

    fun onMinimizedChanged(minimized: Boolean) {
        if (minimized) {
            petMotionController.cancelAutonomousMotion()
        }
        dispatch(PetAction.MinimizedChanged(minimized, SystemClock.uptimeMillis()))
    }

    suspend fun currentBreakStatus(): BreakStatus {
        return breakReminderRepository.breakStatus(
            petId = petId,
            settings = breakReminderSettingsRepository.load(),
            nowMs = System.currentTimeMillis(),
        )
    }

    fun showTestBreakReminder() {
        val settings = breakReminderSettingsRepository.load()
        dispatch(PetAction.BreakReminderShown(SystemClock.uptimeMillis()))
        onUiRequest(
            PetUiRequest.ShowBreakReminder(
                message = "そろそろ休憩しませんか？",
                activeMinutes = settings.intervalMinutes,
                tone = settings.tone,
            ),
        )
    }

    fun acceptBreak() {
        val nowUptimeMs = SystemClock.uptimeMillis()
        val nowWallClockMs = System.currentTimeMillis()
        val settings = breakReminderSettingsRepository.load()
        coroutineScope.launch {
            breakReminderRepository.acceptBreak(petId, settings, nowWallClockMs)
            petMemoryRepository.incrementBreakAccepted(petId, speciesName, nowWallClockMs)
            petBehaviorRepository.record(
                petId = petId,
                species = speciesName,
                occurredAtMs = nowWallClockMs,
                behaviorId = PetBehaviorIds.BREAK_ACCEPTED,
                reason = BehaviorReason.BREAK_DUE,
            )
            dispatch(PetAction.BreakAccepted(nowUptimeMs))
        }
    }

    fun snoozeBreak() {
        val nowUptimeMs = SystemClock.uptimeMillis()
        val nowWallClockMs = System.currentTimeMillis()
        val settings = breakReminderSettingsRepository.load()
        coroutineScope.launch {
            breakReminderRepository.snooze(petId, settings, nowWallClockMs)
            petMemoryRepository.incrementSnooze(petId, speciesName, nowWallClockMs)
            petBehaviorRepository.record(
                petId = petId,
                species = speciesName,
                occurredAtMs = nowWallClockMs,
                behaviorId = PetBehaviorIds.BREAK_SNOOZED,
                reason = BehaviorReason.USER_DISMISSED,
            )
            dispatch(PetAction.BreakSnoozed(nowUptimeMs))
        }
    }

    fun dismissBreakToday() {
        val nowUptimeMs = SystemClock.uptimeMillis()
        val nowWallClockMs = System.currentTimeMillis()
        val settings = breakReminderSettingsRepository.load()
        coroutineScope.launch {
            breakReminderRepository.dismissToday(petId, settings, nowWallClockMs)
            petMemoryRepository.incrementDismiss(petId, speciesName, nowWallClockMs)
            petBehaviorRepository.record(
                petId = petId,
                species = speciesName,
                occurredAtMs = nowWallClockMs,
                behaviorId = PetBehaviorIds.BREAK_DISMISSED,
                reason = BehaviorReason.USER_DISMISSED,
            )
            dispatch(PetAction.BreakDismissed(nowUptimeMs))
        }
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
    }

    private fun runBrainTick() {
        if (brainThinking) return
        val nowUptimeMs = SystemClock.uptimeMillis()
        val nowWallClockMs = System.currentTimeMillis()
        reduceOnly(PetAction.BrainTick(nowUptimeMs))

        val snapshot = state
        val pose = petMotionController.currentPose()
        val context = PetContext(
            petId = petId,
            speciesName = speciesName,
            world = petMotionController.currentWorld(behaviorProfile.species),
            breakReminderSettings = breakReminderSettingsRepository.load(),
            behaviorSettings = petBehaviorSettingsRepository.load(),
            overlayVisible = snapshot.overlay.isVisible,
            isMinimized = snapshot.overlay.isMinimized || petMotionController.isMinimized(),
            isDragging = snapshot.interaction.isDragging || petMotionController.isDragging(),
            hasTransientAnimation = snapshot.body.transientAnimation != null,
            nowWallClockMs = nowWallClockMs,
        )

        brainThinking = true
        coroutineScope.launch {
            val decision = runCatching {
                petBrain.think(snapshot, context, pose, nowUptimeMs)
            }.getOrElse {
                PetDecision.None
            }
            brainThinking = false
            if (running) {
                handleDecision(decision)
            }
        }
    }

    private fun handleDecision(decision: PetDecision) {
        if (state.interaction.isDragging || state.overlay.isMinimized || petMotionController.isDragging() || petMotionController.isMinimized()) {
            return
        }
        val nowUptimeMs = SystemClock.uptimeMillis()
        val nowWallClockMs = System.currentTimeMillis()
        when (decision) {
            PetDecision.None -> Unit
            is PetDecision.PlayAnimation -> {
                dispatch(
                    PetAction.AutonomousAnimationStarted(
                        animationKey = decision.animationKey,
                        durationMs = decision.durationMs,
                        nowMs = nowUptimeMs,
                    ),
                )
                coroutineScope.launch {
                    petBehaviorRepository.record(
                        petId = petId,
                        species = speciesName,
                        occurredAtMs = nowWallClockMs,
                        behaviorId = decision.behaviorId,
                        reason = decision.reason,
                        fromX = petMotionController.currentPose().x,
                        fromY = petMotionController.currentPose().y,
                    )
                }
            }
            is PetDecision.MoveTo -> startMotion(decision.plan, recordBehavior = true)
            is PetDecision.PrepareBreakReminder -> {
                dispatch(PetAction.ReminderProposalPrepared(nowUptimeMs))
                decision.animationKey?.let { animationKey ->
                    dispatch(
                        PetAction.AutonomousAnimationStarted(
                            animationKey = animationKey,
                            durationMs = 1_200L,
                            nowMs = nowUptimeMs,
                        ),
                    )
                }
                decision.plan?.let { startMotion(it, recordBehavior = false) }
            }
            is PetDecision.ShowBreakReminder -> {
                dispatch(PetAction.BreakReminderShown(nowUptimeMs))
                onUiRequest(
                    PetUiRequest.ShowBreakReminder(
                        message = decision.message,
                        activeMinutes = decision.activeMinutes,
                        tone = decision.tone,
                    ),
                )
            }
            PetDecision.ShowBreakStatusPanel -> onUiRequest(PetUiRequest.OpenBreakStatus)
        }
    }

    private fun startMotion(plan: MotionPlan, recordBehavior: Boolean) {
        val nowUptimeMs = SystemClock.uptimeMillis()
        val nowWallClockMs = System.currentTimeMillis()
        dispatch(
            PetAction.AutonomousAnimationStarted(
                animationKey = plan.animationKey,
                durationMs = plan.durationMs + 250L,
                nowMs = nowUptimeMs,
            ),
        )
        dispatch(PetAction.AutonomousMoveStarted(nowUptimeMs))
        if (recordBehavior) {
            coroutineScope.launch {
                petBehaviorRepository.record(
                    petId = petId,
                    species = speciesName,
                    occurredAtMs = nowWallClockMs,
                    behaviorId = behaviorIdFor(plan),
                    locomotionMode = plan.locomotionMode,
                    reason = plan.reason,
                    fromX = plan.fromX,
                    fromY = plan.fromY,
                    toX = plan.targetX,
                    toY = plan.targetY,
                )
            }
        }
        petMotionController.animateTo(plan) {
            if (running) {
                dispatch(PetAction.AutonomousMoveFinished(SystemClock.uptimeMillis()))
            }
        }
    }

    private fun behaviorIdFor(plan: MotionPlan): String {
        return when (behaviorProfile.species) {
            PetSpecies.PANDA -> PetBehaviorIds.PANDA_SLOW_WALK
            PetSpecies.AFRICAN_SCOPS_OWL -> PetBehaviorIds.OWL_PERCH_SHIFT
            PetSpecies.UNKNOWN -> PetBehaviorIds.AUTO_SLEEP
        }
    }

    private fun dispatch(action: PetAction) {
        reduceOnly(action)
        lastStepAtMs = 0L
        renderImmediately()
    }

    private fun reduceOnly(action: PetAction) {
        val wasSleeping = state.need.isSleeping
        val nextState = PetReducer.reduce(state, action)
        if (nextState != state) {
            state = nextState
            onStateChanged(state)
            recordSleepTransitionIfNeeded(wasSleeping, nextState.need.isSleeping, action)
        }
    }

    private fun recordSleepTransitionIfNeeded(
        wasSleeping: Boolean,
        isSleeping: Boolean,
        action: PetAction,
    ) {
        if (wasSleeping == isSleeping) return
        val nowWallClockMs = System.currentTimeMillis()
        val behaviorId = if (isSleeping) PetBehaviorIds.AUTO_SLEEP else PetBehaviorIds.AUTO_WAKE
        val reason = when {
            isSleeping -> BehaviorReason.SLEEP
            action is PetAction.Tap -> BehaviorReason.USER_TAPPED
            else -> BehaviorReason.IDLE
        }
        val pose = petMotionController.currentPose()
        coroutineScope.launch {
            petBehaviorRepository.record(
                petId = petId,
                species = speciesName,
                occurredAtMs = nowWallClockMs,
                behaviorId = behaviorId,
                reason = reason,
                fromX = pose.x,
                fromY = pose.y,
            )
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
        const val FIRST_BRAIN_TICK_DELAY_MS = 1_000L
        const val BRAIN_TICK_MS = 15_000L
    }
}

sealed interface PetUiRequest {
    data object OpenBreakStatus : PetUiRequest

    data class ShowBreakReminder(
        val message: String,
        val activeMinutes: Int,
        val tone: BreakReminderTone,
    ) : PetUiRequest
}
