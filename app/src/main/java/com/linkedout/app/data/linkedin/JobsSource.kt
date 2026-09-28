package com.linkedout.app.data.linkedin

import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.core.model.JobCard
import com.linkedout.app.core.model.JobDetail
import com.linkedout.app.core.network.ErrorMapper
import com.linkedout.app.core.network.HostThrottle
import com.linkedout.app.core.web.ChallengeGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.util.Locale
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

/**
 * LinkedIn's public job search and job pages, the ones its guest jobs site
 * reads. They answer a logged out visitor without the arrival dance profiles
 * need (checked on 2026-09-28: 200 with no cookie and no referrer), so the
 * requests here are plain, and they share the host's throttle with every
 * other read so jobs cannot spend the allowance profiles need.
 *
 * What is searched goes to LinkedIn, as it would from its own site. Nothing
 * else does: no account, no history, no saved search on their side.
 */
class JobsSource(
    private val gateway: ChallengeGateway,
    private val throttle: HostThrottle,
    private val log: RequestLog
) {

    /** One page of results from [start]. An empty list means there are no more. */
    suspend fun search(keywords: String, location: String, start: Int): Outcome<List<JobCard>> {
        val url = buildString {
            append(SEARCH)
            append("?keywords=").append(encode(keywords))
            if (location.isNotBlank()) append("&location=").append(encode(location))
            append("&start=").append(start)
        }
        return read(url, RequestLog.Kind.LIST) { body ->
            val cards = JobsParser.cards(body)
            // A page of nothing is a real answer at the end of the results,
            // but a first page of nothing from a big body is markup that moved.
            if (cards.isEmpty() && start == 0 && body.length > EMPTY_PAGE_LIMIT) {
                Outcome.Failure(AppError.Unknown("No offer could be read from LinkedIn's answer"))
            } else {
                Outcome.Success(cards)
            }
        }
    }

    suspend fun detail(id: String): Outcome<JobDetail> =
        read("$POSTING$id", RequestLog.Kind.PAGE) { body ->
            JobsParser.detail(id, body)?.let { Outcome.Success(it) }
                ?: Outcome.Failure(AppError.PostUnavailable(LinkedInHost.HOST, "This offer could not be read"))
        }

    private suspend fun <T> read(
        url: String,
        kind: RequestLog.Kind,
        parse: (String) -> Outcome<T>
    ): Outcome<T> = withContext(Dispatchers.IO) {
        if (!throttle.acquire(LinkedInHost.HOST)) {
            return@withContext Outcome.Failure(AppError.RateLimited(LinkedInHost.HOST, null))
        }
        val started = System.currentTimeMillis()
        val page = try {
            gateway.getPage(url = url, host = LinkedInHost.HOST, kind = kind, requestHeaders = HEADERS)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            log.record(kind, url, "transport failure", detail = failure.message)
            return@withContext Outcome.Failure(ErrorMapper.fromThrowable(LinkedInHost.HOST, failure))
        }
        log.record(
            kind = kind,
            url = url,
            outcome = if (page.status == 200) "ok" else "http ${page.status}",
            httpStatus = page.status,
            bodyBytes = page.body.length,
            durationMillis = System.currentTimeMillis() - started
        )
        if (page.status == 429 || page.status == ErrorMapper.LINKEDIN_DENIED) {
            throttle.penalise(LinkedInHost.HOST, page.retryAfterSeconds)
        }
        ErrorMapper.fromStatus(LinkedInHost.HOST, url, page.status, page.retryAfterSeconds, page.body)
            ?.let { return@withContext Outcome.Failure(it) }
        parse(page.body)
    }

    private fun encode(value: String) = URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.name())

    private companion object {
        const val SEARCH = "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search"
        const val POSTING = "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/"

        /** Bigger than any empty results fragment LinkedIn sends. */
        const val EMPTY_PAGE_LIMIT = 2_000

        val HEADERS = mapOf(
            "User-Agent" to LinkedInHost.USER_AGENT,
            "Accept" to "text/html,*/*;q=0.8",
            // The reader's own language first, so offers come back worded as on
            // the phone.
            "Accept-Language" to "${Locale.getDefault().toLanguageTag()},en;q=0.8"
        )
    }
}
