package com.kazumaproject.petagent.behavior

data class PetBehaviorSettings(
    val autonomousBehaviorEnabled: Boolean,
    val autonomousMoveEnabled: Boolean,
    val frequency: PetBehaviorFrequency,
)

enum class PetBehaviorFrequency(val preferenceValue: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high");

    companion object {
        fun fromPreference(value: String?): PetBehaviorFrequency {
            return entries.firstOrNull { it.preferenceValue == value } ?: NORMAL
        }
    }
}
