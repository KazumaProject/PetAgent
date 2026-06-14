package com.kazumaproject.petagent.llm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class LocalModelDownloader(
    context: Context,
    private val repository: LocalModelRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext

    suspend fun download(
        metadata: LocalModelMetadata = repository.selectedModel(),
        allowMobileData: Boolean = repository.mobileDataAllowed(),
        onProgress: (Int) -> Unit = {},
    ): Result<String> = withContext(dispatcher) {
        if (metadata.requiresWifiByDefault && !allowMobileData && !canUseCurrentNetwork(allowMobileData)) {
            val message = "Wi-Fi is required before downloading the local conversation model."
            repository.saveFailed(message)
            return@withContext Result.failure(IllegalStateException(message))
        }

        val modelsDir = File(appContext.filesDir, "llm/models").apply { mkdirs() }
        val finalFile = File(modelsDir, metadata.fileName)
        val partialFile = File(modelsDir, "${metadata.fileName}.part")

        try {
            if (finalFile.exists()) {
                val existingIsValid = runCatching {
                    verifyFile(finalFile, metadata)
                }.isSuccess
                if (existingIsValid) {
                    val checksum = metadata.sha256 ?: sha256(finalFile)
                    repository.saveDownloaded(metadata, finalFile.absolutePath, checksum)
                    onProgress(100)
                    return@withContext Result.success(finalFile.absolutePath)
                }
                if (!finalFile.delete()) {
                    error("Unable to replace invalid local model file.")
                }
            }

            var lastFailure: Throwable? = null
            var downloaded = false
            for (attempt in 1..MAX_ATTEMPTS) {
                try {
                    downloadOnce(metadata, partialFile, onProgress)
                    lastFailure = null
                    downloaded = true
                    break
                } catch (error: Throwable) {
                    lastFailure = error
                }
            }

            if (!downloaded) {
                lastFailure?.let { throw it }
            }
            try {
                verifyFile(partialFile, metadata)
            } catch (error: Throwable) {
                partialFile.delete()
                throw error
            }
            if (finalFile.exists() && !finalFile.delete()) {
                error("Unable to replace old model file.")
            }
            if (!partialFile.renameTo(finalFile)) {
                error("Unable to finalize downloaded model.")
            }
            val checksum = metadata.sha256 ?: sha256(finalFile)
            repository.saveDownloaded(metadata, finalFile.absolutePath, checksum)
            onProgress(100)
            Result.success(finalFile.absolutePath)
        } catch (error: Throwable) {
            val message = error.message ?: "Unable to download local model."
            repository.saveFailed(message)
            Result.failure(error)
        }
    }

    private fun downloadOnce(
        metadata: LocalModelMetadata,
        partialFile: File,
        onProgress: (Int) -> Unit,
    ) {
        val existingBytes = partialFile.takeIf { it.exists() }?.length() ?: 0L
        val connection = (URL(metadata.downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "PetAgent/1.0")
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }

        connection.use { conn ->
            val responseCode = conn.responseCode
            if (responseCode !in listOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                error("Model download failed with HTTP $responseCode.")
            }

            val append = existingBytes > 0L && responseCode == HttpURLConnection.HTTP_PARTIAL
            val startingBytes = if (append) existingBytes else 0L
            if (!append && partialFile.exists() && !partialFile.delete()) {
                error("Unable to restart partial model download.")
            }

            val contentLength = conn.getHeaderFieldLong("Content-Length", -1L)
            val expectedTotal = metadata.expectedBytes
                ?: contentLength.takeIf { it > 0L }?.let { startingBytes + it }

            FileOutputStream(partialFile, append).use { output ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = startingBytes
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val progress = if (expectedTotal != null && expectedTotal > 0L) {
                            ((downloaded * 100L) / expectedTotal).toInt().coerceIn(0, 99)
                        } else {
                            0
                        }
                        repository.saveDownloading(progress)
                        onProgress(progress)
                    }
                }
            }
        }
    }

    private fun canUseCurrentNetwork(allowMobileData: Boolean): Boolean {
        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternet) return false
        if (allowMobileData) return true
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun verifyFile(file: File, metadata: LocalModelMetadata) {
        val expectedBytes = metadata.expectedBytes
        if (expectedBytes != null && file.length() != expectedBytes) {
            error("Downloaded model size did not match the expected size.")
        }

        val expectedSha256 = metadata.sha256
        if (expectedSha256 != null && !sha256(file).equals(expectedSha256, ignoreCase = true)) {
            error("Downloaded model checksum did not match.")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private inline fun HttpURLConnection.use(block: (HttpURLConnection) -> Unit) {
        try {
            block(this)
        } finally {
            disconnect()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 128 * 1024
    }
}
