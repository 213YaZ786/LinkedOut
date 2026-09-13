# LinkedOut

Read public LinkedIn posts on Android, with no account, no tracking and no ads.

## What you can do

- **Follow people** without a LinkedIn account. Your list stays on your phone.
- **Read everything in one place.** Home shows everyone you follow, newest first. Pull down to refresh.
- **Filter Home**: media only, hide replies, hide reposts.
- **Look up a profile** from the Accounts tab before deciding to follow it.
- **Open a post** to read it in full with the comments LinkedIn shows a guest, copy its text, share it, or open it on LinkedIn.
- **View photos and videos** full screen, zoom in, and save them to your phone.
- **Read offline.** Posts you have seen are saved on the phone and stay readable without a connection.
- **Choose your look**: light, dark, pure black, text size, compact posts, square avatars.

## How to name someone

LinkedIn has no @handle, and searching it by name needs an account. A person is
named by the last part of their profile address:

```
linkedin.com/in/jane-doe-5b19a2
```

In Accounts, type that part or paste the whole address. Sharing a profile to
LinkedOut from a browser or from the LinkedIn app works too.

Company and school pages are recognised but not read yet. People only.

## Privacy

- No account, no sign in, no ads, no analytics, no crash reporting.
- Three permissions: internet access, network status, and notifications, the
  last one asked only if you turn notifications on.
- One outbound host, `www.linkedin.com`, over https only. **Settings >
  Connection check** names it.
- Who you follow and the posts you saved never leave the phone.

LinkedIn does see your IP address when LinkedOut fetches a page, exactly as a
browser would. There is no way around that while reading its pages.

## What a guest is allowed to see

LinkedIn shows a signed out reader a reduced version of a page and puts a sign
in wall in front of the rest. LinkedOut reads the public version, so some
limits are simply inherited:

- A profile has no paging. LinkedIn serves one slice of recent activity and
  offers no way to ask for more, so scrolling further finds nothing.
- A post page stops at about ten comments.
- Some profiles show a guest nothing at all.

Requests are paced at roughly one per second and back off when LinkedIn asks
for it, because a reader with no account is rate limited hard and has no way to
ask for more.

## Install

Download the APK from the [Releases](https://github.com/213YaZ786/LinkedOut/releases)
page and install it. Android 12 or newer is required.

## Having a problem?

Open **Settings > Activity log**, tap "Save to Downloads", and attach the file
to an issue. It lists what the app asked for and what came back. It contains no
personal data beyond the profiles you opened.

## For developers

Kotlin and Jetpack Compose, single module. Build with Android Studio or:

```
gradle :app:assembleDebug
```

Built with AGP 9.4 (built-in Kotlin) and Gradle 9.7.1, compiling against API 37
with a minimum of API 31. Library updates arrive as Dependabot pull requests.
CI builds a debug APK on every pull request, and on a push to `main` it builds a
signed release, tags it `v<versionName>` and publishes the APK. That needs the
repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and
`KEY_PASSWORD`.

Pages are parsed without a DOM and without a JSON library: `Markup.kt` scans the
HTML and the JSON-LD graph directly, which keeps the parsers runnable outside
Android. There are two independent selector sets, one for the profile page and
one for the post page, since the two share no class name.

Forked from [MTGA](https://github.com/213YaZ786/MTGA), which reads X the same
way.

MIT licensed.
