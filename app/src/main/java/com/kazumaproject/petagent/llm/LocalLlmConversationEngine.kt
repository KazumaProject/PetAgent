package com.kazumaproject.petagent.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.InvocationTargetException

class LocalLlmConversationEngine(
    context: Context,
    private val repository: LocalModelRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PetConversationEngine {
    private val appContext = context.applicationContext
    private val initMutex = Mutex()

    @Volatile
    private var engine: Any? = null

    @Volatile
    private var conversation: Any? = null

    @Volatile
    private var engineModelPath: String? = null

    @Volatile
    private var cancelled = false

    override suspend fun isReady(): Boolean = withContext(dispatcher) {
        val path = repository.localModelPath() ?: return@withContext false
        validateModelFile(path, checkStoredChecksum = false).isSuccess
    }

    override suspend fun generate(
        prompt: String,
        onToken: (String) -> Unit,
    ): Result<String> = withContext(dispatcher) {
        cancelled = false
        val modelPath = repository.localModelPath()
            ?: return@withContext Result.failure(IllegalStateException("Local model has not been downloaded."))
        validateModelFile(modelPath, checkStoredChecksum = true).exceptionOrNull()?.let { error ->
            repository.saveFailed(error.message ?: "Downloaded local model file is invalid.")
            return@withContext Result.failure(error)
        }

        try {
            val activeConversation = ensureConversation(modelPath)
            val fullText = StringBuilder()
            var previousEmission = ""
            val flow = sendMessageFlow(activeConversation, prompt)
            flow.collect { message ->
                if (cancelled) throw CancellationException("Local LLM request cancelled.")
                val emission = message?.extractTextReflectively().orEmpty()
                val token = if (emission.startsWith(previousEmission)) {
                    emission.removePrefix(previousEmission)
                } else {
                    emission
                }
                previousEmission = if (emission.startsWith(previousEmission)) {
                    emission
                } else {
                    previousEmission + emission
                }
                if (token.isNotBlank()) {
                    fullText.append(token)
                    onToken(token)
                }
            }
            Result.success(fullText.toString())
        } catch (error: Throwable) {
            val root = error.rootCause()
            if (root !is CancellationException) {
                val message = root.toUserVisibleMessage()
                Log.e(TAG, "Local LLM conversation failed.", root)
                repository.saveFailed(message)
            }
            Result.failure(root)
        }
    }

    override fun cancel() {
        cancelled = true
        runCatching {
            conversation?.callNoArg("cancelProcess")
        }
    }

    override fun close() {
        cancel()
        conversation?.closeIfPossible()
        conversation = null
        engine?.closeIfPossible()
        engine = null
        engineModelPath = null
    }

    private suspend fun ensureConversation(modelPath: String): Any {
        return initMutex.withLock {
            if (engineModelPath != modelPath) {
                conversation?.closeIfPossible()
                conversation = null
                engine?.closeIfPossible()
                engine = null
                engineModelPath = null
            }

            val activeEngine = engine ?: run {
                repository.saveLoading()
                val cacheDir = File(appContext.cacheDir, "litert_lm").apply { mkdirs() }
                createLiteRtEngine(modelPath, cacheDir.absolutePath).also {
                    try {
                        it.callNoArg("initialize")
                    } catch (error: Throwable) {
                        it.closeIfPossible()
                        throw error
                    }
                    engine = it
                    engineModelPath = modelPath
                    repository.saveReady()
                }
            }

            conversation?.takeIf { it.isAliveConversation() } ?: createConversation(activeEngine).also {
                conversation = it
            }
        }
    }

    private fun createLiteRtEngine(modelPath: String, cacheDir: String): Any {
        val backendClass = Class.forName("com.google.ai.edge.litertlm.Backend")
        val cpuBackend = Class.forName("com.google.ai.edge.litertlm.Backend\$CPU")
            .getConstructor()
            .newInstance()
        val engineConfigClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
        val engineConfig = engineConfigClass
            .getConstructor(
                String::class.java,
                backendClass,
                backendClass,
                backendClass,
                Integer::class.java,
                Integer::class.java,
                String::class.java,
            )
            .newInstance(
                modelPath,
                cpuBackend,
                null,
                null,
                Integer.valueOf(MAX_CONTEXT_TOKENS),
                null,
                cacheDir,
            )

        return Class.forName("com.google.ai.edge.litertlm.Engine")
            .getConstructor(engineConfigClass)
            .newInstance(engineConfig)
    }

    private fun createConversation(engine: Any): Any {
        val conversationConfigClass = Class.forName("com.google.ai.edge.litertlm.ConversationConfig")
        val conversationConfig = conversationConfigClass.getConstructor().newInstance()
        return engine.javaClass
            .getMethod("createConversation", conversationConfigClass)
            .invoke(engine, conversationConfig)
    }

    @Suppress("UNCHECKED_CAST")
    private fun sendMessageFlow(conversation: Any, prompt: String): Flow<Any?> {
        val method = conversation.javaClass.methods.firstOrNull { method ->
            method.name == "sendMessageAsync" &&
                method.returnType.name == "kotlinx.coroutines.flow.Flow" &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                Map::class.java.isAssignableFrom(method.parameterTypes[1])
        } ?: error("LiteRT-LM streaming API is unavailable.")
        return method.invoke(conversation, prompt, emptyMap<String, Any>()) as Flow<Any?>
    }

    private fun Any.extractTextReflectively(): String {
        val contents = callNoArg("getContents") ?: return toString()
        val contentItems = contents.callNoArg("getContents") as? List<*> ?: return contents.toString()
        return contentItems.joinToString(separator = "") { content ->
            content?.callNoArg("getText")?.toString() ?: content.toString()
        }
    }

    private fun Any.isAliveConversation(): Boolean {
        return callNoArg("isAlive") as? Boolean ?: false
    }

    private fun Any.closeIfPossible() {
        runCatching {
            (this as? AutoCloseable)?.close()
        }
    }

    private fun Any.callNoArg(name: String): Any? {
        return javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.isEmpty() }
            ?.invoke(this)
    }

    private fun validateModelFile(
        path: String,
        checkStoredChecksum: Boolean,
    ): Result<Unit> {
        val file = File(path)
        if (!file.exists()) {
            return Result.failure(IllegalStateException("Downloaded local model file is missing."))
        }

        val metadata = repository.selectedModel()
        if (file.name != metadata.fileName) {
            return Result.failure(
                IllegalStateException(
                    "Selected local model has changed. Please download the current model.",
                ),
            )
        }

        val expectedBytes = metadata.expectedBytes
        if (expectedBytes != null && file.length() != expectedBytes) {
            return Result.failure(
                IllegalStateException(
                    "Local model file is incomplete. Please download it again.",
                ),
            )
        }

        val expectedSha256 = metadata.sha256
        val storedChecksum = repository.storedChecksum()
        if (
            checkStoredChecksum &&
            expectedSha256 != null &&
            !storedChecksum.isNullOrBlank() &&
            !storedChecksum.equals(expectedSha256, ignoreCase = true)
        ) {
            return Result.failure(
                IllegalStateException(
                    "Local model checksum does not match. Please download it again.",
                ),
            )
        }

        return Result.success(Unit)
    }

    private fun Throwable.rootCause(): Throwable {
        var current = this
        while (true) {
            current = when (current) {
                is InvocationTargetException -> current.targetException ?: current.cause ?: return current
                else -> current.cause ?: return current
            }
        }
    }

    private fun Throwable.toUserVisibleMessage(): String {
        val detail = message?.takeIf { it.isNotBlank() }
        val type = javaClass.name
        return when {
            detail != null -> "Local conversation failed: $detail"
            else -> "Local conversation failed: $type"
        }
    }

    private companion object {
        const val TAG = "LocalLlmConversation"
        const val MAX_CONTEXT_TOKENS = 2_048
    }
}
