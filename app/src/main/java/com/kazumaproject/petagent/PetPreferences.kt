package com.kazumaproject.petagent

import android.content.Context

object PetPreferences {
    const val PREFS_NAME = "pet_agent_settings"
    const val KEY_SELECTED_PET_ID = "selected_pet_id"
    const val KEY_SELECTED_PET_BASE_PATH = "selected_pet_base_path"
    const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    const val DEFAULT_PET_ID = "default_african_scops_owl"
    const val DEFAULT_PET_BASE_PATH = "default_african_scops_owl"

    fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun petSizeKey(petId: String) = "pet_size_dp_$petId"
}
