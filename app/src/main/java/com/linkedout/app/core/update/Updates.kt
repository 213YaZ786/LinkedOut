package com.linkedout.app.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
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
 * nothing in the background. Installing downloads the release APK, checks
 * it against the release's SHA256SUMS, and hands it to Android's installer,
 * which asks the reader to confirm; the key that signed it must be the one
 * that signed the installed app, or Android refuses.
 */
object Updates {

    private const val REPO = "213YaZ786/LinkedOut"
    private const val ACTION_STATUS = "com.linkedout.app.UPDATE_STATUS"
    private const val TIMEOUT_MS = 15_000
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
                page = (root["html_url"] as? JsonPrimitive)?.content ?: "https://github.com/$REPO/releases/latest",
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
     * when anything fails, the checksum above all: a file that does not
     * match is deleted, never installed.
     */
    suspend fun install(context: Context, release: Release): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val apk = release.apk ?: return@runCatching false
            val name = apk.substringAfterLast('/')
            val expected = get(release.sums ?: return@runCatching false).decodeToString().lines()
                .map { it.trim() }
                .firstOrNull { it.endsWith(name) }
                ?.substringBefore(' ')?.lowercase() ?: return@runCatching false
            val file = File(context.cacheDir, "update.apk")
            download(apk, file)
            if (sha256(file) != expected) {
                file.delete()
                return@runCatching false
            }
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
            file.delete()
            true
        }.getOrDefault(false)
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

    private fun download(url: String, into: File) = open(url).run {
        try {
            if (responseCode != 200) error("http $responseCode")
            inputStream.use { input -> into.outputStream().use { input.copyTo(it) } }
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
