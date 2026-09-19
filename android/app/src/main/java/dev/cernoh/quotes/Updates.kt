package dev.cernoh.quotes

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * The update path for a sideloaded app: read the latest GitHub release, compare
 * it with the installed version, download the APK, and hand it to the system
 * installer.
 *
 * The repository is private, so the release API needs a token. Without one the
 * API answers 404, which this reports as a missing token rather than as a
 * network fault.
 *
 * Every method here blocks. Call them from a worker thread.
 */
object Updates {
    private const val OWNER = "cernoh"
    private const val REPO = "quotes"
    private const val USER_AGENT = "quotes-android"
    private const val TIMEOUT_MS = 20_000

    data class Release(
        val tag: String,
        val name: String,
        val notes: String,
        /** The API id of the APK asset, used to download it from a private repository. */
        val apkId: Long?,
        val apkName: String?,
    )

    sealed interface Result {
        /** The installed version is the latest one. */
        data class UpToDate(val version: String) : Result

        /** A newer release exists. */
        data class Available(val installed: String, val release: Release) : Result

        /** The check could not answer. */
        data class Failed(val message: String) : Result
    }

    fun installedVersion(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"

    /** The latest release on GitHub, or null when the repository holds none. */
    fun check(context: Context): Result {
        val installed = installedVersion(context)
        val body = try {
            val connection = open(
                "https://api.github.com/repos/$OWNER/$REPO/releases/latest",
                Prefs.githubToken(context),
            )
            when (val code = connection.responseCode) {
                200 -> connection.inputStream.bufferedReader().use { it.readText() }
                404 -> return Result.Failed(
                    "GitHub reports no release. The repository is private, so add a " +
                        "token below, or check it for a published release.",
                )
                401, 403 -> return Result.Failed("GitHub refused the token ($code).")
                else -> return Result.Failed("GitHub replied $code.")
            }
        } catch (error: Exception) {
            return Result.Failed("No answer from GitHub: ${error.message ?: error.javaClass.simpleName}")
        }

        val release = parseRelease(body)
            ?: return Result.Failed("GitHub sent a release this app cannot read.")

        return when {
            release.tag.isBlank() -> Result.Failed("The latest release has no tag.")
            newer(release.tag, installed) -> Result.Available(installed, release)
            else -> Result.UpToDate(installed)
        }
    }

    /**
     * Read the fields this app needs out of a GitHub release payload. The first
     * asset that ends in `.apk` is the update.
     */
    fun parseRelease(body: String): Release? = try {
        val json = JSONObject(body)
        val assets = json.optJSONArray("assets")
        var id: Long? = null
        var name: String? = null
        if (assets != null) {
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val assetName = asset.optString("name")
                val assetId = asset.optLong("id", 0L)
                if (assetName.endsWith(".apk") && assetId > 0L) {
                    name = assetName
                    id = assetId
                    break
                }
            }
        }
        Release(
            tag = json.optString("tag_name"),
            name = json.optString("name"),
            notes = json.optString("body"),
            apkId = id,
            apkName = name,
        )
    } catch (error: Exception) {
        null
    }

    /** True when [candidate] is a later version than [installed]. */
    fun newer(candidate: String, installed: String): Boolean {
        val left = version(candidate)
        val right = version(installed)
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** "v0.2.1" and "0.2.1" both become [0, 2, 1]. */
    private fun version(text: String): List<Int> =
        text.trim().removePrefix("v").removePrefix("V")
            .split('.', '-', '+')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** Download the APK of [release] into the cache. */
    fun download(context: Context, release: Release): File {
        val assetId = release.apkId ?: error("the release carries no APK")
        val target = File(context.cacheDir, release.apkName ?: "update.apk")
        if (target.exists()) target.delete()
        // The browser_download_url of a private repository needs an
        // authenticated web session, so download through the API asset endpoint
        // with the token. GitHub answers with a redirect to a signed address.
        val connection = open(assetUrl(assetId), Prefs.githubToken(context), binary = true)
        val code = connection.responseCode
        check(code == 200) { "the download answered $code" }
        connection.inputStream.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        check(target.length() > 0) { "the download is empty" }
        return target
    }

    /** The API address of one release asset. */
    fun assetUrl(assetId: Long): String =
        "https://api.github.com/repos/$OWNER/$REPO/releases/assets/$assetId"

    /**
     * Hand the APK to the system installer. The user sees the normal install
     * screen; this app never installs anything by itself.
     */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        )
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("quotes-update", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val result = Intent(context, UpdateResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            session.commit(PendingIntent.getBroadcast(context, 51, result, flags).intentSender)
        }
    }

    private fun open(url: String, token: String, binary: Boolean = false): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty(
            "Accept",
            if (binary) "application/octet-stream" else "application/vnd.github+json",
        )
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (token.isNotEmpty()) connection.setRequestProperty("Authorization", "Bearer $token")
        return connection
    }
}
