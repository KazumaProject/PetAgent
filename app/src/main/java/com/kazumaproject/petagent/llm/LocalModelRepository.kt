package com.kazumaproject.petagent.llm

import android.content.Context
import java.io.File

class LocalModelRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun selectedModel(): LocalModelMetadata {
        return LocalModelCatalog.byId(
            prefs.getString(KEY_SELECTED_MODEL_ID, LocalModelCatalog.recommended.id)
                ?: LocalModelCatalog.recommended.id,
        )
    }

    fun selectModel(metadata: LocalModelMetadata) {
        prefs.edit()
            .putString(KEY_SELECTED_MODEL_ID, metadata.id)
            .apply()
    }

    fun state(): LocalModelState {
        val status = prefs.getString(KEY_STATUS, STATUS_NOT_DOWNLOADED) ?: STATUS_NOT_DOWNLOADED
        val path = localModelPath()
        val metadata = selectedModel()
        return when (status) {
            STATUS_DOWNLOADING -> LocalModelState.Downloading(
                prefs.getInt(KEY_DOWNLOAD_PROGRESS, 0).coerceIn(0, 100),
            )
            STATUS_DOWNLOADED -> if (path != null && isLocalModelAvailable(path, metadata)) {
                LocalModelState.Downloaded(path)
            } else {
                LocalModelState.NotDownloaded
            }
            STATUS_LOADING -> LocalModelState.Loading
            STATUS_READY -> if (path != null && isLocalModelAvailable(path, metadata)) {
                LocalModelState.Ready
            } else {
                LocalModelState.NotDownloaded
            }
            STATUS_FAILED -> LocalModelState.Failed(
                prefs.getString(KEY_FAILURE_MESSAGE, "Local model setup failed.").orEmpty(),
            )
            else -> LocalModelState.NotDownloaded
        }
    }

    fun localModelPath(): String? {
        return prefs.getString(KEY_MODEL_PATH, null)?.takeIf { it.isNotBlank() }
    }

    fun storedChecksum(): String? {
        return prefs.getString(KEY_MODEL_CHECKSUM, null)?.takeIf { it.isNotBlank() }
    }

    fun mobileDataAllowed(): Boolean {
        return prefs.getBoolean(KEY_ALLOW_MOBILE_DATA, false)
    }

    fun setMobileDataAllowed(allowed: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_MOBILE_DATA, allowed).apply()
    }

    fun saveDownloading(progress: Int) {
        prefs.edit()
            .putString(KEY_STATUS, STATUS_DOWNLOADING)
            .putInt(KEY_DOWNLOAD_PROGRESS, progress.coerceIn(0, 100))
            .remove(KEY_FAILURE_MESSAGE)
            .apply()
    }

    fun saveDownloaded(metadata: LocalModelMetadata, path: String, checksum: String?) {
        prefs.edit()
            .putString(KEY_SELECTED_MODEL_ID, metadata.id)
            .putString(KEY_STATUS, STATUS_DOWNLOADED)
            .putString(KEY_MODEL_PATH, path)
            .putString(KEY_MODEL_CHECKSUM, checksum.orEmpty())
            .putInt(KEY_DOWNLOAD_PROGRESS, 100)
            .remove(KEY_FAILURE_MESSAGE)
            .apply()
    }

    fun saveLoading() {
        prefs.edit()
            .putString(KEY_STATUS, STATUS_LOADING)
            .remove(KEY_FAILURE_MESSAGE)
            .apply()
    }

    fun saveReady() {
        prefs.edit()
            .putString(KEY_STATUS, STATUS_READY)
            .remove(KEY_FAILURE_MESSAGE)
            .apply()
    }

    fun saveFailed(message: String) {
        prefs.edit()
            .putString(KEY_STATUS, STATUS_FAILED)
            .putString(KEY_FAILURE_MESSAGE, message)
            .apply()
    }

    fun clear() {
        prefs.edit()
            .putString(KEY_STATUS, STATUS_NOT_DOWNLOADED)
            .remove(KEY_MODEL_PATH)
            .remove(KEY_MODEL_CHECKSUM)
            .remove(KEY_FAILURE_MESSAGE)
            .putInt(KEY_DOWNLOAD_PROGRESS, 0)
            .apply()
    }

    private fun isLocalModelAvailable(path: String, metadata: LocalModelMetadata): Boolean {
        val file = File(path)
        if (!file.exists()) return false
        if (file.name != metadata.fileName) return false
        val expectedBytes = metadata.expectedBytes
        if (expectedBytes != null && file.length() != expectedBytes) return false
        val expectedSha256 = metadata.sha256
        val checksum = storedChecksum()
        if (
            expectedSha256 != null &&
            !checksum.isNullOrBlank() &&
            !checksum.equals(expectedSha256, ignoreCase = true)
        ) {
            return false
        }
        return true
    }

    private companion object {
        const val PREFS_NAME = "petagent_local_model"
        const val KEY_SELECTED_MODEL_ID = "selected_model_id"
        const val KEY_MODEL_PATH = "model_path"
        const val KEY_MODEL_CHECKSUM = "model_checksum"
        const val KEY_STATUS = "status"
        const val KEY_DOWNLOAD_PROGRESS = "download_progress"
        const val KEY_FAILURE_MESSAGE = "failure_message"
        const val KEY_ALLOW_MOBILE_DATA = "allow_mobile_data"

        const val STATUS_NOT_DOWNLOADED = "not_downloaded"
        const val STATUS_DOWNLOADING = "downloading"
        const val STATUS_DOWNLOADED = "downloaded"
        const val STATUS_LOADING = "loading"
        const val STATUS_READY = "ready"
        const val STATUS_FAILED = "failed"
    }
}
