package com.linkedout.app.data.linkedin

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
    const val USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/140.0.0.0 Safari/537.36"

    /**
     * True when the body is the redirect stub rather than a page. Checked on
     * the body, never on the status, which is 200 either way.
     */
    fun isAuthWall(body: String): Boolean {
        if (body.length > 20_000) return false
        return "/authwall" in body || "sessionRedirect=" in body
    }

    /** True when the body is a page this app knows how to read. */
    fun isProfilePage(body: String): Boolean = ProfilePageParser.PAGE_MARKER in body
}
