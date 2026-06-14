package com.kazumaproject.petagent.petpack

data class PetCatalog(
    val formatVersion: Int,
    val pets: List<PetCatalogEntry>,
)

data class PetCatalogEntry(
    val petId: String,
    val basePath: String,
    val displayName: String,
    val preview: String,
)
