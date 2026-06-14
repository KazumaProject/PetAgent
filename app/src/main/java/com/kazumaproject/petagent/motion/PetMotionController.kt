package com.kazumaproject.petagent.motion

import com.kazumaproject.petagent.behavior.PetSpecies
import com.kazumaproject.petagent.overlay.PetOverlayController

class PetMotionController(
    private val overlayController: PetOverlayController,
) {
    fun currentPose(): PetPose = overlayController.currentPose()

    fun currentWorld(species: PetSpecies): PetWorld = overlayController.currentWorld(species)

    fun animateTo(plan: MotionPlan, onFinished: (() -> Unit)? = null) {
        overlayController.animateTo(plan, onFinished)
    }

    fun playSequence(
        sequence: MotionSequence,
        currentPose: PetPose,
        world: PetWorld,
        onAnimation: (key: String, restart: Boolean) -> Unit,
        onFinished: () -> Unit,
    ) {
        overlayController.playSequence(
            sequence = sequence,
            currentPose = currentPose,
            world = world,
            onAnimation = onAnimation,
            onFinished = onFinished,
        )
    }

    fun cancelAutonomousMotion() {
        overlayController.cancelAutonomousMotion()
    }

    fun isMinimized(): Boolean = overlayController.isMinimized()

    fun isDragging(): Boolean = overlayController.isDragging()
}
