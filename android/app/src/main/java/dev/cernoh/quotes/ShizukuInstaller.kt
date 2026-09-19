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
    /** What the user sees in the settings. */
    enum class State {
        /** The Shizuku app is not present. */
        NOT_INSTALLED,

        /** Shizuku is installed but its service is not running. */
        NOT_RUNNING,

        /** The service runs, and this app has no permission yet. */
        DENIED,

        /** Everything is in place. */
        READY,
    }

    fun state(): State = try {
        when {
            !Shizuku.pingBinder() -> State.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> State.READY
            else -> State.DENIED
        }
    } catch (error: Throwable) {
        // No Shizuku app, or its provider is not reachable at all.
        State.NOT_INSTALLED
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
    fun install(apk: File): String {
        check(state() == State.READY) { "Shizuku is not ready" }
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

    /** A message for the settings screen. */
    fun describe(state: State): String = when (state) {
        State.NOT_INSTALLED -> "Shizuku is not installed."
        State.NOT_RUNNING -> "Shizuku is installed, but its service is not running."
        State.DENIED -> "Shizuku runs. This app needs your permission."
        State.READY -> "Shizuku is ready. Updates can install without a prompt."
    }
}
