package com.linkedout.app.core.model

/**
 * A job offer as the public search lists it. [postedDate] is the ISO day the
 * card carries, when it carries one.
 */
data class JobCard(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    val postedDate: String? = null,
    val logoUrl: String? = null,
    /** The offer's own page, which is also where the reader applies. */
    val url: String,
    // https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/parse.py#parse_search_fragment
    // JobCard(badges=tuple(badges), ...): what the card says of the offer ("Actively hiring"), at most four.
    val badges: List<String> = emptyList()
)

// The choices of LinkedIn's guest filter bar, with the values it is sent, ported whole from
// https://github.com/spinlud/py-linkedin-jobs-scraper/blob/master/linkedin_jobs_scraper/filters/filters.py
// (MIT) and read against fetchaller's maps, which agree: TimeFilters, TypeFilters, ExperienceLevelFilters,
// OnSiteOrRemoteFilters. [label] is the words the app shows.
// not ported: RelevanceFilters (LinkedIn ignores the sort), IndustryFilters, SalaryBaseFilters, JobFunctionFilters,
// BenefitsFilters, CommitmentsFilters (not in the validated menu), TypeFilters VOLUNTEER and OTHER (fetchaller:
// "their meaning was never established")
enum class TimeFilters(val value: String, val label: String) {
    ANY("", "Any time"),
    DAY("r86400", "Past 24 hours"),
    WEEK("r604800", "Past week"),
    MONTH("r2592000", "Past month")
}

enum class TypeFilters(val value: String, val label: String) {
    ANY("", "Any"),
    FULL_TIME("F", "Full-time"),
    PART_TIME("P", "Part-time"),
    TEMPORARY("T", "Temporary"),
    CONTRACT("C", "Contract"),
    INTERNSHIP("I", "Internship")
}

enum class ExperienceLevelFilters(val value: String, val label: String) {
    ANY("", "Any"),
    INTERNSHIP("1", "Internship"),
    ENTRY_LEVEL("2", "Entry level"),
    ASSOCIATE("3", "Associate"),
    MID_SENIOR("4", "Mid-Senior level"),
    DIRECTOR("5", "Director"),
    EXECUTIVE("6", "Executive")
}

enum class OnSiteOrRemoteFilters(val value: String, val label: String) {
    ANY("", "Any"),
    ON_SITE("1", "On-site"),
    REMOTE("2", "Remote"),
    HYBRID("3", "Hybrid")
}

// The filters of search_linkedin_jobs (date_posted, workplace, experience, job_type, easy_apply, under_10_applicants),
// each Any by default, which adds nothing to the request:
// https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/search.py#search_linkedin_jobs
// not ported: keywords, location, geo_id, strict_location, min_salary (dollars), sort, start, limit, max_tokens, timeout,
// browser_solver (the search itself is JobsSource's)
data class JobFilters(
    val date_posted: TimeFilters = TimeFilters.ANY,
    val workplace: OnSiteOrRemoteFilters = OnSiteOrRemoteFilters.ANY,
    val experience: ExperienceLevelFilters = ExperienceLevelFilters.ANY,
    val job_type: TypeFilters = TypeFilters.ANY,
    val easy_apply: Boolean = false,
    val under_10_applicants: Boolean = false
) {
    /** How many are set, shown on the Filters pill. */
    val count: Int
        get() = listOf(
            date_posted != TimeFilters.ANY,
            workplace != OnSiteOrRemoteFilters.ANY,
            experience != ExperienceLevelFilters.ANY,
            job_type != TypeFilters.ANY,
            easy_apply,
            under_10_applicants
        ).count { it }
}

// What search_linkedin_jobs collects: the cards, plus the count that the /jobs/search page heads its results with
// (joaosilvalopes/linkedin-easy-apply-bot selectors jobCount, read by JobsParser):
// https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/search.py#search_linkedin_jobs
// where `collected` is the list of cards. [count] is as LinkedIn words it ("Over 2,000 offers"), null when absent.
data class JobsPage(val collected: List<JobCard>, val count: String? = null)

/** The offer read from its own guest page. */
data class JobDetail(
    val id: String,
    val title: String,
    val company: String,
    // https://github.com/Averyy/fetchaller-mcp/blob/main/src/fetchaller/linkedin/parse.py#JobDetail
    // company_url: the company's page, without LinkedIn's tracking query, "" when the offer names none.
    val companyUrl: String = "",
    val location: String,
    /** As LinkedIn words it, "2 months ago", in the language it answered in. */
    val postedAgo: String? = null,
    // applicants: "75 applicants" as LinkedIn words it, "" when the page gives none.
    val applicants: String = "",
    val description: String,
    /** Seniority, contract type, function, industry, each as label and value. */
    val criteria: List<Pair<String, String>> = emptyList(),
    val url: String
)
