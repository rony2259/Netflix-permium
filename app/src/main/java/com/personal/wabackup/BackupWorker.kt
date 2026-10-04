package com.personal.wabackup

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.wabackup.util.BackupSourceFinder
import com.personal.wabackup.util.SettingsStore
import com.personal.wabackup.util.TelegramClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class BackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = SettingsStore(applicationContext)

        try {
            val tgEnabled = settings.tgBotToken.isNotBlank() && settings.tgChatId.isNotBlank()
            if (!tgEnabled) {
                return@withContext Result.failure()
            }

            val tgUsable = TelegramClient.ensureChatId(settings)
            if (!tgUsable) {
                return@withContext Result.retry()
            }

            return@withContext runTelegramDirect(settings)

        } catch (e: Exception) {
            return@withContext Result.failure()
        }
    }

    private fun runTelegramDirect(settings: SettingsStore): Result {
        val finder = BackupSourceFinder(applicationContext)
        val candidates = finder.findOnFilesystem().sortedByDescending { it.modified }
            .take(MAX_FILES_PER_RUN)
        if (candidates.isEmpty()) {
            return Result.retry()
        }

        val stateFile = File(applicationContext.filesDir, "tg_state.json")
        val sent: Map<String, Long> = try {
            val o = JSONObject(stateFile.readText())
            o.keys().asSequence().associateWith { o.optLong(it) }
        } catch (_: Exception) { emptyMap() }

        val stageDir = File(applicationContext.filesDir, "staging").apply { mkdirs() }
        var delivered = 0
        var skipped = 0
        var failed = 0
        val newState = JSONObject()
        for ((k, v) in sent) newState.put(k, v)
        for (c in candidates) {
            val name = c.name()
            if (sent[name] == c.sizeBytes) { skipped++; continue }

            val staged = File(stageDir, sanitize(name))
            try {
                if (c.file != null) {
                    if (!c.file.copyToWithRetry(staged)) { failed++; continue }
                } else if (c.uri != null) {
                    if (!copyUriToFile(c.uri, staged)) { failed++; continue }
                } else continue

                if (TelegramClient.sendFile(settings, staged, name)) {
                    newState.put(name, c.sizeBytes)
                    delivered++
                } else failed++
            } finally {
                staged.delete()
            }
        }
        try { stateFile.writeText(newState.toString()) } catch (_: Exception) {}
        settings.lastUploadOkMillis = System.currentTimeMillis()

        return if (failed > 0 && delivered == 0) Result.retry() else Result.success()
    }

    private fun copyUriToFile(uri: Uri, dest: File): Boolean = try {
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } != null && dest.length() > 0
    } catch (_: Exception) {
        false
    }

    private fun File.copyToWithRetry(dest: File, attempts: Int = 2): Boolean {
        repeat(attempts) {
            try {
                copyTo(dest, overwrite = true)
                if (dest.length() == length()) return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)

    companion object {
        private const val MAX_FILES_PER_RUN = 10
    }
}
