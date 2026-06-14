package com.kazumaproject.petagent.llm

data class LocalModelMetadata(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val expectedBytes: Long? = null,
    val sha256: String? = null,
    val requiresWifiByDefault: Boolean = true,
)

object LocalModelCatalog {
    val recommended: LocalModelMetadata = LocalModelMetadata(
        id = "litert-community/gemma-4-E2B-it-litert-lm",
        displayName = "Gemma 4 E2B LiteRT-LM",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true",
        expectedBytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
    )

    val models: List<LocalModelMetadata> = listOf(recommended)

    fun byId(id: String): LocalModelMetadata {
        return models.firstOrNull { it.id == id } ?: recommended
    }
}
