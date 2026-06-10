package com.silica.assistant.core.debug

enum class DebugTier {
    VISION,
    TEXT_AI,
    APP_AI,
    ERROR
}

data class CommentDebugEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val appName: String,
    val contextHint: String?,
    val promptSent: String,
    val response: String?,
    val tier: DebugTier,
    val durationMs: Long,
    val screenshotUsed: Boolean = false,
    val errorMessage: String? = null,
    val provider: String? = null
)

object CommentDebugger {
    private val _log = mutableListOf<CommentDebugEntry>()
    val log: List<CommentDebugEntry> get() = _log.toList()

    fun record(entry: CommentDebugEntry) {
        _log.add(entry)
        if (_log.size > 200) {
            _log.removeAt(0)
        }
    }

    fun clear() {
        _log.clear()
    }
}
