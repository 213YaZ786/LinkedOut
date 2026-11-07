package com.linkedout.app.data.linkedin

import com.linkedout.app.core.common.AppError
import com.linkedout.app.core.common.Outcome
import com.linkedout.app.core.debug.RequestLog
import com.linkedout.app.core.model.ExperienceLevelFilters
import com.linkedout.app.core.model.JobDetail
import com.linkedout.app.core.model.JobFilters
import com.linkedout.app.core.model.JobsPage
import com.linkedout.app.core.model.OnSiteOrRemoteFilters
import com.linkedout.app.core.model.TimeFilters
import com.linkedout.app.core.model.TypeFilters
import com.linkedout.app.data.linkedin.JobsParser.parse_search_fragment
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

    /**
     * One page of results from [start], with the filters. No card means there are no more.
     *
     * https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/search.py#search_linkedin_jobs
     * One request to the public search page returns 60 cards, six times the fragment endpoint's 10, and the page
     * ignores `start`, so it serves the first page only (start 0, with the count in its head). The next ones come from
     * the fragment endpoint with `start` as an absolute row offset, here the number of cards already loaded.
     * not ported: the geoId resolution (the typeahead call), strict_location (a second filter on the place), the
     * `sort == "recent"` client-side sort, the fixed pool of 100 cards (the reader asks for each page), the
     * deadline and timeout (the gateway has its own), the render to markdown
     */
    suspend fun search_linkedin_jobs(
        keywords: String,
        location: String,
        filters: JobFilters = JobFilters(),
        start: Int
    ): Outcome<JobsPage> {
        // Rows 0 to 999 answer, 1000 and more is an HTTP 400 with no body: the end of the results, not an error.
        if (start > MAX_START) return Outcome.Success(JobsPage(listOf()))
        val base_params = _build_params(
            keywords, location, filters.date_posted, filters.workplace, filters.experience, filters.job_type,
            start, filters.easy_apply, filters.under_10_applicants
        )
        if (start == 0) {
            val page = fetch_search_page(base_params)
            if (page is Outcome.Success && page.value.collected.isNotEmpty()) return page
            // The page gave no card, a wall or a layout that moved: the fragment endpoint, as the app always read it.
            // Its answer wins unless it fails, then the page's own answer stands (an empty page is "no offer matches").
            val fragment = fetch_search_fragment(base_params, start)
            if (fragment is Outcome.Failure && page is Outcome.Success) return page
            return fragment
        }
        return fetch_search_fragment(base_params, start)
    }

    private suspend fun fetch_search_page(params: List<Pair<String, String>>): Outcome<JobsPage> =
        read(SEARCH_PAGE + "?" + urlencode(params), RequestLog.Kind.LIST) { page_html ->
            Outcome.Success(JobsPage(parse_search_fragment(page_html), JobsParser.jobCount(page_html)))
        }

    private suspend fun fetch_search_fragment(params: List<Pair<String, String>>, start: Int): Outcome<JobsPage> =
        read(SEARCH + "?" + urlencode(params), RequestLog.Kind.LIST) { fragment ->
            val collected = parse_search_fragment(fragment)
            // A page of nothing is a real answer at the end of the results,
            // but a first page of nothing from a big body is markup that moved.
            if (collected.isEmpty() && start == 0 && fragment.length > EMPTY_PAGE_LIMIT) {
                Outcome.Failure(AppError.Unknown("No offer could be read from LinkedIn's answer"))
            } else {
                Outcome.Success(JobsPage(collected))
            }
        }

    suspend fun detail(id: String): Outcome<JobDetail> =
        read("$POSTING$id", RequestLog.Kind.PAGE) { body ->
            JobsParser.parse_job_detail(id, body)?.let { Outcome.Success(it) }
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
        } else if (page.status == 200) {
            throttle.clear(LinkedInHost.HOST)
        }
        ErrorMapper.fromStatus(LinkedInHost.HOST, url, page.status, page.retryAfterSeconds, page.body)
            ?.let { return@withContext Outcome.Failure(it) }
        parse(page.body)
    }

    private fun encode(value: String) = URLEncoder.encode(value.trim(), StandardCharsets.UTF_8.name())

    /**
     * The query string of [query], in the order given, each name and value quoted as quote_plus does (URLEncoder).
     * https://github.com/python/cpython/blob/main/Lib/urllib/parse.py#urlencode
     * not ported: doseq (no value here is a sequence)
     * not ported: safe (nothing is left unquoted)
     * not ported: encoding (UTF-8 always)
     * not ported: errors (UTF-8 always)
     * not ported: specified (a word of the docstring, the function quote_via names is the parameter here)
     */
    private fun urlencode(query: List<Pair<String, String>>, quote_via: (String) -> String = ::encode): String {
        val l = mutableListOf<String>()
        for ((k, v) in query) {
            l.add(quote_via(k) + "=" + quote_via(v))
        }
        return l.joinToString("&")
    }

    private companion object {
        /** The public search page: 60 cards and the count, first page only. */
        const val SEARCH_PAGE = "https://www.linkedin.com/jobs/search"

        /** The last row LinkedIn answers (fetchaller's docs/site-apis.md: rows 0 to 999). */
        const val MAX_START = 999

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

// The value each choice is sent as, fetchaller's maps (the keys are the filters' enums, ported from spinlud):
// https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/search.py#_build_params
internal val DATE_POSTED = TimeFilters.entries.associateWith { it.value }
internal val WORKPLACE = OnSiteOrRemoteFilters.entries.associateWith { it.value }
internal val EXPERIENCE = ExperienceLevelFilters.entries.associateWith { it.value }
internal val JOB_TYPE = TypeFilters.entries.associateWith { it.value }

// Boolean filters, labelled by LinkedIn's own logged-out filter bar (f_AL Easy Apply, f_EA Under 10 applicants).
internal const val EASY_APPLY_PARAM = "f_AL"
internal const val FEW_APPLICANTS_PARAM = "f_EA"

/**
 * The parameters of a search, in the order fetchaller puts them: start, keywords, location, then each filter that is set.
 * Any adds nothing. No f_SB2 (salary, dollars only) and no sort (LinkedIn ignores it).
 * https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/search.py#_build_params
 * not ported: geo_id (the location text is sent, the typeahead call is not made)
 * not ported: min_salary (f_SB2, dollars)
 * not ported: MIN_SALARY (f_SB2, dollars)
 */
internal fun _build_params(
    keywords: String,
    location: String,
    date_posted: TimeFilters,
    workplace: OnSiteOrRemoteFilters,
    experience: ExperienceLevelFilters,
    job_type: TypeFilters,
    start: Int,
    easy_apply: Boolean = false,
    under_10_applicants: Boolean = false
): List<Pair<String, String>> {
    val params = mutableListOf("start" to start.toString())
    if (keywords.isNotBlank()) params.add("keywords" to keywords.trim())
    if (location.isNotBlank()) params.add("location" to location.trim())
    if (!DATE_POSTED[date_posted].isNullOrEmpty()) params.add("f_TPR" to DATE_POSTED.getValue(date_posted))
    if (!WORKPLACE[workplace].isNullOrEmpty()) params.add("f_WT" to WORKPLACE.getValue(workplace))
    if (!EXPERIENCE[experience].isNullOrEmpty()) params.add("f_E" to EXPERIENCE.getValue(experience))
    if (!JOB_TYPE[job_type].isNullOrEmpty()) params.add("f_JT" to JOB_TYPE.getValue(job_type))
    if (easy_apply) params.add(EASY_APPLY_PARAM to "true")
    if (under_10_applicants) params.add(FEW_APPLICANTS_PARAM to "true")
    return params
}
