package com.linkedout.app.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** What the app does when a newer release is out, asked once at launch. */
@Serializable
enum class UpdateMode { OFF, NOTIFY, INSTALL }

/** A release on GitHub: its version, its page, its APK and checksum file. */
data class Release(val version: String, val page: String, val apk: String?, val sums: String?)

/**
 * The app's own releases on GitHub. One small request when the app opens,
 * nothing in the background. Installing downloads the release APK, shown
 * in a notification, checks it (see install) and hands it to Android's
 * installer, which asks the user to confirm when it must.
 */
object Updates {

    private const val REPO = "213YaZ786/LinkedOut"
    private const val ACTION_STATUS = "com.linkedout.app.UPDATE_STATUS"
    private const val TIMEOUT_MS = 15_000
    /** Far above any release of these apps, to stop an endless download. */
    private const val MAX_APK = 200L * 1024 * 1024
    internal const val PREFS = "updates"
    internal const val INSTALLING = "installing"

    private val json = Json { ignoreUnknownKeys = true }

    /** The newest release, or null when GitHub cannot be read. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        runCatching {
            val root = json.parseToJsonElement(
                get("https://api.github.com/repos/$REPO/releases/latest").decodeToString()
            ) as JsonObject
            val tag = (root["tag_name"] as? JsonPrimitive)?.content ?: return@runCatching null
            // A rebuild of the same version is tagged v1.2.3-b45.
            val version = tag.removePrefix("v").substringBefore("-")
            val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            fun asset(name: String) = assets.firstOrNull { (it["name"] as? JsonPrimitive)?.content == name }
                ?.let { (it["browser_download_url"] as? JsonPrimitive)?.content }
            Release(
                version = version,
                // Only this app's own page is ever opened from here.
                page = (root["html_url"] as? JsonPrimitive)?.content?.takeIf { it.startsWith("https://github.com/$REPO/") }
                    ?: "https://github.com/$REPO/releases/latest",
                apk = asset("LinkedOut-$version.apk"),
                sums = asset("SHA256SUMS.txt")
            )
        }.getOrNull()
    }

    /** Whether [candidate] is a later version than [current], number by number. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = numbers(candidate)
        val b = numbers(current.substringBefore('-'))
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String) = version.split('.').map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /** Whether Android lets this app install packages; the reader allows it once. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Android's own screen where the reader allows it. */
    fun allowInstalls(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Downloads [release], checks it and asks Android to install it. False
     * when anything fails; a file that fails a check is deleted, never
     * installed. Three checks: the file comes from this app's releases on
     * GitHub, it matches the release's SHA256SUMS, and it is this app, newer,
     * signed with the key of the installed one (Android refuses another key
     * anyway; checking first means the user is never asked for a bad file).
     */
    suspend fun install(context: Context, release: Release): Boolean = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "update.apk")
        // The download shows in a notification, the app open or not.
        val notice = UpdateNotice(context.applicationContext, release.version)
        var done = false
        try {
            val apk = release.apk?.takeIf(::fromRepo) ?: return@withContext false
            val sums = release.sums?.takeIf(::fromRepo) ?: return@withContext false
            val name = apk.substringAfterLast('/')
            val expected = get(sums).decodeToString().lines()
                .map { it.trim() }
                .firstOrNull { it.endsWith(name) }
                ?.substringBefore(' ')?.lowercase() ?: return@withContext false
            notice.progress(0f)
            download(apk, file, notice::progress)
            if (sha256(file) != expected || !sameApp(context, file)) return@withContext false
            val app = context.applicationContext
            listenForConfirmation(app)
            // Lets UpdatedReceiver tell this update from one installed by hand.
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(INSTALLING, true).apply()
            val installer = app.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(app.packageName)
                // Android skips the question when this app installed the one
                // being replaced; otherwise it still asks.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("app.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val status = Intent(ACTION_STATUS).setPackage(app.packageName)
                val pending = PendingIntent.getBroadcast(
                    app, id, status, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                session.commit(pending.intentSender)
            }
            done = true
            true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            false
        } finally {
            file.delete()
            // Android's installer takes over from here; a failure stays
            // in the notification, which opens the release page.
            if (done) notice.cancel() else notice.failed(release.page)
        }
    }

    /** Only files attached to this app's own releases are fetched. */
    internal fun fromRepo(url: String): Boolean = url.startsWith("https://github.com/$REPO/releases/download/")

    /**
     * Whether [file] is this app, a later version, signed with every key
     * the installed app is signed with.
     */
    private fun sameApp(context: Context, file: File): Boolean {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return false
        if (archive.packageName != context.packageName) return false
        val installed = pm.getPackageInfo(context.packageName, 0)
        if (archive.longVersionCode <= installed.longVersionCode) return false
        val signers = archive.signingInfo?.apkContentsSigners.orEmpty()
        return signers.isNotEmpty() && signers.all {
            pm.hasSigningCertificate(context.packageName, it.toByteArray(), PackageManager.CERT_INPUT_RAW_X509)
        }
    }

    @Volatile
    private var listening = false

    /**
     * Android answers the install with the screen that asks the reader: open
     * it. One receiver for the life of the process, however many tries.
     */
    private fun listenForConfirmation(app: Context) {
        if (listening) return
        listening = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) return
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "LinkedOut")
            setRequestProperty("Accept", "application/vnd.github+json, */*")
        }

    private fun get(url: String): ByteArray = open(url).run {
        try {
            if (responseCode != 200) error("http $responseCode")
            inputStream.use { it.readBytes() }
        } finally {
            disconnect()
        }
    }

    private fun download(url: String, into: File, onProgress: (Float) -> Unit) = open(url).run {
        try {
            if (responseCode != 200) error("http $responseCode")
            val total = contentLengthLong
            if (total > MAX_APK) error("too large")
            inputStream.use { input ->
                into.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var shown = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        read += n
                        if (read > MAX_APK) error("too large")
                        out.write(buffer, 0, n)
                        // Once per percent, not once per buffer.
                        val percent = if (total > 0) (read * 100 / total).toInt() else -1
                        if (percent != shown) {
                            shown = percent
                            onProgress(if (total > 0) read.toFloat() / total else 0f)
                        }
                    }
                }
            }
        } finally {
            disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
