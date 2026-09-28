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
    val url: String
)

/** The offer read from its own guest page. */
data class JobDetail(
    val id: String,
    val title: String,
    val company: String,
    val location: String,
    /** As LinkedIn words it, "2 months ago", in the language it answered in. */
    val postedAgo: String? = null,
    val description: String,
    /** Seniority, contract type, function, industry, each as label and value. */
    val criteria: List<Pair<String, String>> = emptyList(),
    val url: String
)
