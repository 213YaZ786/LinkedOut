package com.linkedout.app.core.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * An in memory record of every request LinkedOut makes and what came back.
 *
 * Exists so a failure can be reported with evidence instead of a description.
 * Bounded to [CAPACITY] entries, kept in memory only, never written anywhere
 * unless you explicitly export it, and it stores no identifiers beyond the URLs
 * the app itself requested.
 */
class RequestLog {

    data class Entry(
        val atMillis: Long,
        val kind: Kind,
        val url: String,
        val httpStatus: Int? = null,
        val bodyBytes: Int? = null,
        val durationMillis: Long? = null,
        val outcome: String,
        val detail: String? = null
    )

    enum class Kind { PROBE, PROFILE, PAGE, RSS, PARSE, THREAD, LIST }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun record(entry: Entry) {
        _entries.value = (_entries.value + entry).takeLast(CAPACITY)
    }

    fun record(
        kind: Kind,
        url: String,
        outcome: String,
        httpStatus: Int? = null,
        bodyBytes: Int? = null,
        durationMillis: Long? = null,
        detail: String? = null
    ) = record(
        Entry(
            atMillis = System.currentTimeMillis(),
            kind = kind,
            url = url,
            httpStatus = httpStatus,
            bodyBytes = bodyBytes,
            durationMillis = durationMillis,
            outcome = outcome,
            detail = detail
        )
    )

    fun clear() {
        _entries.value = emptyList()
    }

    /** Plain text, newest last, suitable for pasting anywhere. */
    fun render(): String {
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return buildString {
            appendLine("LinkedOut request log")
            appendLine("entries: ${_entries.value.size}")
            appendLine("=".repeat(60))
            _entries.value.forEach { e ->
                appendLine("[${stamp.format(Date(e.atMillis))}] ${e.kind}  ${e.outcome}")
                appendLine("  url: ${e.url}")
                val meta = buildList {
                    e.httpStatus?.let { add("http $it") }
                    e.durationMillis?.let { add("${it}ms") }
                    e.bodyBytes?.let { add("$it bytes") }
                }
                if (meta.isNotEmpty()) appendLine("  ${meta.joinToString("  ")}")
                e.detail?.let { appendLine("  $it") }
                appendLine()
            }
        }
    }

    private companion object {
        const val CAPACITY = 300
    }
}
