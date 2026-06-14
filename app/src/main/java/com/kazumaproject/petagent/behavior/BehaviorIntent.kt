package com.kazumaproject.petagent.behavior

sealed interface BehaviorIntent {
    data object IdleMicroMotion : BehaviorIntent
    data object LookAround : BehaviorIntent
    data object Nap : BehaviorIntent
    data class Wander(val reason: BehaviorReason, val urgency: Float) : BehaviorIntent
    data class ApproachForBreakReminder(val reason: BehaviorReason) : BehaviorIntent
    data class ReactToTap(val tapX: Float, val tapY: Float) : BehaviorIntent
    data object SocialPrompt : BehaviorIntent
}
