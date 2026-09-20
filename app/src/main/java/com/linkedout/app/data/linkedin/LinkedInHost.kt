package com.linkedout.app.data.linkedin

import com.linkedout.app.core.web.ClientHints

/**
 * The one host this app talks to, and how to tell a real page from a wall.
 *
 * MTGA had a pool of interchangeable servers. There is no equivalent here:
 * LinkedIn has no mirrors, no front ends and no feeds, so every request goes to
 * linkedin.com from the phone and there is nowhere to fail over to. That is why
 * this file is a handful of constants rather than a pool.
 *
 * The wall is worth describing precisely, because it is not an HTTP failure.
 * LinkedIn answers 200 with a page whose whole body is a script that reads
 * document.referrer and sends the browser to /authwall. A client that does not
 * run scripts receives the stub and nothing else. So the status code says the
 * request succeeded, the page says otherwise, and only [isAuthWall] can tell
 * the difference.
 */
object LinkedInHost {

    const val HOST = "www.linkedin.com"
    const val BASE = "https://$HOST"

    /**
     * The suffix the guest cookies are allowed on, and the only domain this
     * app keeps a cookie for at all. LinkedIn sets `bcookie` on the bare
     * domain, so the host alone would refuse its own session.
     */
    const val COOKIE_DOMAIN = "linkedin.com"

    /**
     * Sent as Referer. LinkedIn serves the full page to a reader arriving from
     * a search engine and the wall to one arriving from nowhere, which is why
     * the same link opens on the second attempt after going back to the
     * results: the second click carries a referrer the first did not.
     */
    const val REFERER = "https://www.google.com/"

    /**
     * A desktop browser string. LinkedIn serves a stripped page to anything it
     * reads as a phone, with no activity section at all, so asking as a phone
     * would cost exactly the content this app exists to show.
     */
    const val CHROME_MAJOR = "140"
    const val CHROME_FULL = "140.0.0.0"

    const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$CHROME_FULL Safari/537.36"

    /**
     * The client hints that go with [USER_AGENT], field for field.
     *
     * A string alone is half a claim. Chromium sends Sec-CH-UA, and on request
     * the platform, the architecture and the rest, and a WebView fills them
     * from the real phone however the string is rewritten. Desktop Chrome on
     * Linux beside an Android WebView that says it is mobile is a combination
     * no browser produces, and it is the difference between this app and
     * Firefox, which the wall lets through.
     *
     * Built from the same two constants as the string above, so the two
     * cannot drift apart in a later edit.
     */
    val CLIENT_HINTS = ClientHints(
        brands = listOf(
            // The third brand is deliberate nonsense, as Chrome's own is:
            // it exists so parsers do not assume a fixed list.
            ClientHints.Brand("Chromium", CHROME_MAJOR, CHROME_FULL),
            ClientHints.Brand("Google Chrome", CHROME_MAJOR, CHROME_FULL),
            ClientHints.Brand("Not;A=Brand", "99", "99.0.0.0")
        ),
        fullVersion = CHROME_FULL,
        platform = "Linux",
        platformVersion = "6.6.0",
        architecture = "x86",
        bitness = 64,
        model = "",
        mobile = false
    )

    /**
     * Main frame addresses the browser engine is not allowed to follow when it
     * reads a page for this app.
     *
     * The wall is not a status and not a bot check. It is a script on the page
     * that sends the browser to /authwall, and a browser obeys it, which is
     * why the engine was kept off the reading path for so long. Refusing these
     * navigations leaves the document that was already served in place.
     *
     * Every entry is a sign in, sign up or checkpoint route, so none of them
     * is a page this app reads and refusing them cannot cost a document.
     */
    val WALL_PATHS = listOf(
        "/authwall",
        "/login",
        "/signup",
        "/checkpoint",
        "/uas/login",
        "/uas/login-submit",
        "/uas/request-password-reset",
        "/m/login"
    )

    /**
     * True when what came back is the wall rather than the page. Checked on
     * the body, never on the status, which is 200 either way.
     *
     * The wall comes in two shapes and only one of them is small.
     *
     * The stub is a body that is nothing but a script reading document.referrer
     * and navigating to /authwall. The size guard belongs to that case: a real
     * page can mention /authwall in a link, so the bare string is only trusted
     * when there is nothing else in the document.
     *
     * The other shape is a full sign up page, sixty thousand characters of
     * form, footer and language picker, which sailed straight past that guard
     * and was then handed to a parser that found no profile in it. The reader
     * saw "this page can't be read" and a retry often worked, because a retry
     * starts the referrer ladder again. That page names itself twice, in its
     * pageKey and in its canonical link, and both are exact enough to trust at
     * any size.
     */
    fun isAuthWall(body: String): Boolean {
        if ("content=\"auth_wall" in body) return true
        if ("rel=\"canonical\" href=\"/authwall\"" in body) return true
        if (pageKey(body)?.let(::isWallPageKey) == true) return true
        if (body.length > 20_000) return false
        return "/authwall" in body || "sessionRedirect=" in body
    }

    /**
     * The page's own name for itself. LinkedIn reshuffles class names often
     * and this meta rarely, which is why the organisation parser keys on it
     * too.
     */
    fun pageKey(body: String): String? {
        val marker = "name=\"pageKey\" content=\""
        val start = body.indexOf(marker)
        if (start < 0) return null
        val from = start + marker.length
        val end = body.indexOf('"', from)
        return if (end < 0) null else body.substring(from, end)
    }

    /**
     * The sign up page answers with a pageKey of its own, and it is not
     * `auth_wall`. The capture that proved it reads `d_registration-cold-join`,
     * with a canonical link pointing at the home page and sixty eight thousand
     * characters of form, so every earlier test missed it: too large for the
     * size guard, wrong canonical, no `auth_wall` anywhere.
     *
     * It arrived as a 200 with no PARSE line behind it, twice in one log,
     * which is the signature of a body the parser was handed and found nothing
     * in. Matched on the family rather than the exact key, because
     * `cold-join` is one of several and they all mean the same thing.
     */
    private fun isWallPageKey(key: String): Boolean {
        val lower = key.lowercase()
        return WALL_PAGE_KEYS.any { it in lower }
    }

    /**
     * Kept narrow on purpose. A real profile answers
     * `d_flagship3_profile_view_base` and an organisation
     * `d_flagship3_company`, so none of these can appear on a page worth
     * reading, and a page wrongly called a wall costs the reader the account.
     */
    private val WALL_PAGE_KEYS = listOf("registration", "authwall", "auth_wall", "d_checkpoint")

    /** True when the body is a page this app knows how to read. */
    fun isProfilePage(body: String): Boolean = ProfilePageParser.PAGE_MARKER in body
}
