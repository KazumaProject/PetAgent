package com.kazumaproject.petagent.agent

import android.os.Handler
import android.os.Looper

sealed interface AgentEvent {
    data object ThinkingStarted : AgentEvent
    data class TokenReceived(val token: String) : AgentEvent
    data class ResponseCompleted(val fullText: String) : AgentEvent
    data class Error(val message: String) : AgentEvent
}

class StubAgentCore(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private var activeRequestId = 0
    private var requestCount = 0
    private val pending = mutableListOf<Runnable>()

    fun submit(prompt: String, listener: (AgentEvent) -> Unit) {
        cancel()
        val requestId = ++activeRequestId
        requestCount += 1
        listener(AgentEvent.ThinkingStarted)

        if (requestCount % ERROR_EVERY_N_REQUESTS == 0) {
            postForRequest(requestId, delayMs = 850L) {
                listener(AgentEvent.Error("The little owl lost that thought."))
            }
            return
        }

        val tokens = buildResponse(prompt)
        val received = StringBuilder()
        tokens.forEachIndexed { index, token ->
            postForRequest(requestId, delayMs = 650L + index * 170L) {
                received.append(token)
                listener(AgentEvent.TokenReceived(token))
            }
        }

        postForRequest(requestId, delayMs = 850L + tokens.size * 170L) {
            listener(AgentEvent.ResponseCompleted(received.toString()))
        }
    }

    fun cancel() {
        pending.forEach { handler.removeCallbacks(it) }
        pending.clear()
        activeRequestId += 1
    }

    fun unload() {
        cancel()
    }

    private fun postForRequest(requestId: Int, delayMs: Long, block: () -> Unit) {
        lateinit var runnable: Runnable
        runnable = Runnable {
            pending.remove(runnable)
            if (requestId == activeRequestId) {
                block()
            }
        }
        pending += runnable
        handler.postDelayed(runnable, delayMs)
    }

    private fun buildResponse(prompt: String): List<String> {
        val topic = prompt.ifBlank { "your screen" }
        return listOf(
            "I ",
            "am ",
            "watching ",
            topic,
            " ",
            "with ",
            "very ",
            "round ",
            "eyes.",
        )
    }

    private companion object {
        const val ERROR_EVERY_N_REQUESTS = 5
    }
}
