package com.linkedout.app.data.marks

import com.linkedout.app.core.common.writeTextAtomically
import com.linkedout.app.core.model.JobCard
import com.linkedout.app.core.model.JobDetail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// Ported from
// https://github.com/213YaZ786/LinkedOut/blob/main/app/src/main/java/com/linkedout/app/data/marks/PostMarks.kt#PostMarks
// not ported: toggleLike (an offer is saved or not: [toggle] is the one mark)
// not ported: toggleArchive (an offer has no archive, only the save)
// not ported: isLiked and isArchived (isSaved is the one question)
// not ported: the Context of the constructor (the folder is given, so a test can use a temporary one)

/** An offer the reader saved: what the Jobs list needs to show it and to open it again. */
@Serializable
data class SavedJob(
    val id: String,
    val title: String,
    val company: String = "",
    val location: String = "",
    val logoUrl: String? = null,
    val url: String,
    /** When it was saved, milliseconds since the epoch. */
    val savedAt: Long = 0L
) {
    /** The card the Jobs list draws, which opens the offer as any other. */
    fun toCard() = JobCard(id = id, title = title, company = company, location = location, logoUrl = logoUrl, url = url)
}

/** Offers the reader saved, newest first. */
@Serializable
data class Saved(val jobs: List<SavedJob> = emptyList()) {
    fun isSaved(id: String) = jobs.any { it.id == id }
}

/**
 * Saved offers, kept on this phone only: nothing is sent anywhere. A copy of what the card shows is kept, so a saved
 * offer stays when LinkedIn stops listing it, and opens from the Jobs screen's Saved view with no search.
 */
class SavedJobs(
    directory: File,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis
) {

    private val file = File(directory, "saved_jobs.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val writing = Mutex()

    private val _saved = MutableStateFlow(read())
    val saved: StateFlow<Saved> = _saved.asStateFlow()

    /** Saves the offer, or removes it when it is saved already. */
    fun toggle(card: JobCard) = change { s ->
        if (s.isSaved(card.id)) s.copy(jobs = s.jobs.filterNot { it.id == card.id })
        else s.copy(
            jobs = listOf(
                SavedJob(card.id, card.title, card.company, card.location, card.logoUrl, card.url, now())
            ) + s.jobs
        )
    }

    /** The same from the offer's own page, which has no logo: the one already kept, if any, stays. */
    fun toggle(detail: JobDetail) = toggle(
        JobCard(
            id = detail.id,
            title = detail.title,
            company = detail.company,
            location = detail.location,
            logoUrl = _saved.value.jobs.firstOrNull { it.id == detail.id }?.logoUrl,
            url = detail.url
        )
    )

    private fun change(transform: (Saved) -> Saved) {
        val next = transform(_saved.value)
        _saved.value = next
        scope.launch { writing.withLock { file.writeTextAtomically(json.encodeToString(Saved.serializer(), next)) } }
    }

    private fun read(): Saved =
        runCatching { json.decodeFromString(Saved.serializer(), file.readText()) }.getOrDefault(Saved())
}
