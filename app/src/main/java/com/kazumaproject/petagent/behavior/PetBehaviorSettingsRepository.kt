package com.kazumaproject.petagent.behavior

import android.content.Context
import com.kazumaproject.petagent.PetPreferences

class PetBehaviorSettingsRepository(context: Context) {
    private val prefs = PetPreferences.prefs(context.applicationContext)

    fun load(): PetBehaviorSettings {
        return PetBehaviorSettings(
            autonomousBehaviorEnabled = prefs.getBoolean(
                PetPreferences.KEY_AUTONOMOUS_BEHAVIOR_ENABLED,
                PetPreferences.DEFAULT_AUTONOMOUS_BEHAVIOR_ENABLED,
            ),
            autonomousMoveEnabled = prefs.getBoolean(
                PetPreferences.KEY_AUTONOMOUS_MOVE_ENABLED,
                PetPreferences.DEFAULT_AUTONOMOUS_MOVE_ENABLED,
            ),
            frequency = PetBehaviorFrequency.fromPreference(
                prefs.getString(
                    PetPreferences.KEY_AUTONOMOUS_BEHAVIOR_FREQUENCY,
                    PetPreferences.DEFAULT_AUTONOMOUS_BEHAVIOR_FREQUENCY,
                ),
            ),
            approachBeforeBreakEnabled = prefs.getBoolean(
                PetPreferences.KEY_APPROACH_BEFORE_BREAK_ENABLED,
                PetPreferences.DEFAULT_APPROACH_BEFORE_BREAK_ENABLED,
            ),
        )
    }
}
