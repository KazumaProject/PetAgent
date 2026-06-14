package com.kazumaproject.petagent.motion

data class PetPose(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val facing: Facing,
    val isMoving: Boolean,
)

enum class Facing {
    LEFT,
    RIGHT,
    FORWARD,
}
