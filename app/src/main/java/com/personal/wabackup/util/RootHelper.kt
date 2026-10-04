package com.personal.wabackup.util

import java.io.DataOutputStream

/** Minimal root helper. All methods are no-ops on non-rooted devices. */
object RootHelper {

    fun isRooted(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/su/bin/su", "/system/app/Superuser.apk",
        )
        if (paths.any { java.io.File(it).exists() }) return true
        return try {
            runSu(listOf("id")) && true
        } catch (_: Exception) {
            false
        }
    }

    /** Run a sequence of commands through `su`. Returns true if exit code was 0. */
    fun runSu(commands: List<String>): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                for (c in commands) {
                    os.writeBytes(c + "\n")
                }
                os.writeBytes("exit\n")
                os.flush()
            }
            process.waitFor() == 0
        } catch (_: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }
}
