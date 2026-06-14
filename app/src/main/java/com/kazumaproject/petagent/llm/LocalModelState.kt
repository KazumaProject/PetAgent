package com.kazumaproject.petagent.llm

sealed interface LocalModelState {
    data object NotDownloaded : LocalModelState
    data class Downloading(val progress: Int) : LocalModelState
    data class Downloaded(val path: String) : LocalModelState
    data object Loading : LocalModelState
    data object Ready : LocalModelState
    data class Failed(val message: String) : LocalModelState
}
