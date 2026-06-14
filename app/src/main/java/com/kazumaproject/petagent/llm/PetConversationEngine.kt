package com.kazumaproject.petagent.llm

interface PetConversationEngine {
    suspend fun isReady(): Boolean

    suspend fun generate(
        prompt: String,
        onToken: (String) -> Unit,
    ): Result<String>

    fun cancel()

    fun close()
}
