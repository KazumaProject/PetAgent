package com.kazumaproject.petagent.petpack

import android.content.Context
import android.util.Log
import com.kazumaproject.petagent.PetPreferences
import org.json.JSONObject

class PetCatalogLoader(
    private val context: Context,
) {
    fun load(): PetCatalog {
        return try {
            parse(JSONObject(readAssetText(CATALOG_PATH)))
        } catch (error: Throwable) {
            Log.e(TAG, "Unable to read pet catalog; using default pet only.", error)
            PetCatalog(
                formatVersion = 1,
                pets = listOf(
                    PetCatalogEntry(
                        petId = PetPreferences.DEFAULT_PET_ID,
                        basePath = PetPreferences.DEFAULT_PET_BASE_PATH,
                        displayName = "African Scops Owl",
                        preview = "${PetPreferences.DEFAULT_PET_BASE_PATH}/preview.png",
                    ),
                ),
            )
        }
    }

    private fun parse(json: JSONObject): PetCatalog {
        val petsJson = json.optJSONArray("pets")
        val pets = buildList {
            if (petsJson == null) return@buildList
            for (index in 0 until petsJson.length()) {
                val petJson = petsJson.optJSONObject(index) ?: continue
                val petId = petJson.optString("petId").trim()
                val basePath = petJson.optString("basePath").trim()
                if (petId.isBlank() || basePath.isBlank()) {
                    Log.w(TAG, "Skipping catalog pet at index $index; missing petId or basePath.")
                    continue
                }

                add(
                    PetCatalogEntry(
                        petId = petId,
                        basePath = basePath,
                        displayName = petJson.optString("displayName", petId).ifBlank { petId },
                        preview = petJson.optString("preview"),
                    ),
                )
            }
        }

        return PetCatalog(
            formatVersion = json.optInt("formatVersion", 1),
            pets = pets.ifEmpty {
                listOf(
                    PetCatalogEntry(
                        petId = PetPreferences.DEFAULT_PET_ID,
                        basePath = PetPreferences.DEFAULT_PET_BASE_PATH,
                        displayName = "African Scops Owl",
                        preview = "${PetPreferences.DEFAULT_PET_BASE_PATH}/preview.png",
                    ),
                )
            },
        )
    }

    private fun readAssetText(path: String): String {
        return context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private companion object {
        const val TAG = "PetCatalogLoader"
        const val CATALOG_PATH = "pet_catalog.json"
    }
}
