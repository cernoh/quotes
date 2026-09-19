package dev.cernoh.quotes

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.File

/**
 * Installs an update through Shizuku, when the user turned that on.
 *
 * Shizuku runs a command as the shell user, which Android grants the right to
 * install packages. The command streams the APK on its standard input, so the
 * file does not need to be readable by anyone else:
 *
 *     pm install -r -S <size>
 *
 * Every method here blocks. Call them from a worker thread.
 */
object ShizukuInstaller {
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    /** What the user sees in the settings. */
    enum class State {
        /** The Shizuku app is not present. */
        NOT_INSTALLED,

        /** Shizuku is installed, but its service does not run. */
        NOT_RUNNING,

        /** The service runs, and this app has no permission yet. */
        DENIED,

        /** Everything is in place. */
        READY,
    }

    fun state(context: Context): State = try {
        when {
            // pingBinder() answers false rather than throwing when nothing runs,
            // so the installed package decides whether Shizuku is present at all.
            // Without that check a user who never installed Shizuku would read
            // "installed, but not running" and chase a service that is absent.
            !isInstalled(context) -> State.NOT_INSTALLED
            !Shizuku.pingBinder() -> State.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> State.READY
            else -> State.DENIED
        }
    } catch (error: Throwable) {
        // The Shizuku provider answered a moment ago and is unreachable now.
        State.NOT_INSTALLED
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (error: PackageManager.NameNotFoundException) {
        false
    }

    /** Ask the user for the permission. The answer arrives at [Shizuku]'s listener. */
    fun requestPermission(requestCode: Int): Boolean = try {
        Shizuku.requestPermission(requestCode)
        true
    } catch (error: Throwable) {
        false
    }

    /**
     * Install [apk] without a prompt, and return what `pm` printed. Throws when
     * the install fails, so the caller can show the reason.
     */
    fun install(context: Context, apk: File): String {
        check(state(context) == State.READY) { "Shizuku is not ready" }
        val size = apk.length()
        check(size > 0) { "the update file is empty" }
        val process = Shizuku.newProcess(
            arrayOf("pm", "install", "-r", "-S", size.toString()),
            null,
            null,
        )
        apk.inputStream().use { input ->
            process.outputStream.use { output -> input.copyTo(output) }
        }
        val output = process.inputStream.bufferedReader().readText() +
            process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        check(code == 0 && output.contains("Success")) {
            output.trim().ifEmpty { "pm install exited with $code" }
        }
        return output.trim()
    }
}
