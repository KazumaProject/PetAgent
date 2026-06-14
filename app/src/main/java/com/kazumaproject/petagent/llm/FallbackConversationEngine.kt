package com.kazumaproject.petagent.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Fallback-only conversation engine for development and no-runtime builds.
 */
class FallbackConversationEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PetConversationEngine {
    @Volatile
    private var cancelled = false

    override suspend fun isReady(): Boolean = true

    override suspend fun generate(
        prompt: String,
        onToken: (String) -> Unit,
    ): Result<String> = withContext(dispatcher) {
        cancelled = false
        val response = buildResponse(prompt)
        val fullText = StringBuilder()
        try {
            response.forEach { token ->
                if (cancelled) throw CancellationException("Fallback conversation cancelled.")
                delay(90L)
                fullText.append(token)
                onToken(token)
            }
            Result.success(fullText.toString())
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    override fun cancel() {
        cancelled = true
    }

    override fun close() {
        cancel()
    }

    private fun buildResponse(prompt: String): List<String> {
        val topic = prompt.lineSequence()
            .lastOrNull { it.isNotBlank() }
            ?.take(MAX_TOPIC_CHARS)
            ?: "your screen"
        return listOf(
            "I ",
            "can ",
            "talk ",
            "locally ",
            "after ",
            "a ",
            "model ",
            "is ",
            "installed. ",
            "For ",
            "now, ",
            "I ",
            "heard: ",
            topic,
        )
    }

    private companion object {
        const val MAX_TOPIC_CHARS = 80
    }
}
