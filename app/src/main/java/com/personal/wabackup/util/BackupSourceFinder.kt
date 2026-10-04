package com.personal.wabackup.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.personal.wabackup.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Discovers WhatsApp backup artifacts that are legally reachable by a NON-root app.
 *
 * IMPORTANT / REALITY CHECK:
 *  - WhatsApp's live SQLite database (msgstore.db) lives in /data/data/com.whatsapp/databases/
 *    which is sandboxed. A normal app CANNOT read it. Only WhatsApp itself can copy it into
 *    its own backup (.crypt14) file via Settings > Chats > Chat backup.
 *  - What we CAN do (with the user's cooperation):
 *      1. Read WhatsApp's own encrypted backup files (.crypt##) from
 *         Android/media/WhatsApp/Media/.Backups/ — requires "All files access"
 *         (MANAGE_EXTERNAL_STORAGE) OR a SAF folder grant picked by the user.
 *      2. Read chat exports (.txt / .zip) that the user created with
 *         WhatsApp > chat menu > More > Export chat (usually lands in Documents/ or Downloads/).
 *      3. On a ROOTED phone, run `su -c cp` to grab msgstore.db + the crypt14 keys — this app
 *         includes an optional rooted path, but never silently; the user enables it in settings.
 */
class BackupSourceFinder(private val context: Context) {

    data class Candidate(
        val label: String,
        val uri: Uri?,
        val file: File?,
        val sizeBytes: Long,
        val modified: Long,
        val kind: Kind,
    ) {
        enum class Kind { CRYPT_BACKUP, EXPORT_ZIP, EXPORT_TXT, ROOT_DB }
        fun name(): String =
            uri?.lastPathSegment?.substringAfterLast('/')
                ?: file?.name ?: "unknown"
    }

    /** Well-known WhatsApp shared-storage locations (Android 11+ layout first). */
    private val primaryDir: File? = Environment.getExternalStorageDirectory()

    private val waBackupDirs: List<File> = listOfNotNull(
        primaryDir?.let { File(it, "Android/media/com.whatsapp/WhatsApp/Media/.Backups") },
        primaryDir?.let { File(it, "WhatsApp/Media/.Backups") },                 // legacy layout
        primaryDir?.let { File(it, "Download/WhatsApp") },
    )

    private val exportDirs: List<File> = listOfNotNull(
        primaryDir?.let { File(it, "Documents") },
        primaryDir?.let { File(it, "Download") },
    )

    /** Scan direct filesystem paths (works when MANAGE_EXTERNAL_STORAGE was granted). */
    fun findOnFilesystem(): List<Candidate> {
        val out = ArrayList<Candidate>()

        for (dir in waBackupDirs) {
            if (!dir.isDirectory) continue
            dir.listFiles { f -> f.isFile && f.name.matches(Regex("msgstore.*\\.crypt\\d*")) }
                ?.forEach { f ->
                    out += Candidate(
                        label = context.getString(R.string.label_crypt_backup),
                        uri = null, file = f, sizeBytes = f.length(),
                        modified = f.lastModified(), kind = Candidate.Kind.CRYPT_BACKUP,
                    )
                }
        }

        for (dir in exportDirs) {
            if (!dir.isDirectory) continue
            dir.listFiles { f ->
                f.isFile && (f.name.endsWith(".zip", true) || f.name.endsWith(".txt", true)) &&
                        f.name.contains("whatsapp", ignoreCase = true)
            }?.forEach { f ->
                val kind = if (f.name.endsWith(".zip", true)) Candidate.Kind.EXPORT_ZIP
                else Candidate.Kind.EXPORT_TXT
                out += Candidate(
                    label = context.getString(R.string.label_chat_export),
                    uri = null, file = f, sizeBytes = f.length(),
                    modified = f.lastModified(), kind = kind,
                )
            }
        }
        return out.sortedByDescending { it.modified }
    }

    /**
     * SAF-based scan for a user-picked tree (e.g. the WhatsApp folder). Used when
     * "All files access" is NOT granted. The persisted Uri permission must already exist.
     */
    fun findUnderPickedTree(tree: Uri): List<Candidate> {
        val out = ArrayList<Candidate>()
        val root = DocumentFile.fromTreeUri(context, tree) ?: return out
        walk(root, 0, out)
        return out.sortedByDescending { it.modified }
    }

    private fun walk(doc: DocumentFile, depth: Int, out: MutableList<Candidate>) {
        if (depth > 6) return
        for (child in doc.listFiles()) {
            if (child.isDirectory) {
                walk(child, depth + 1, out)
            } else {
                val n = child.name?.lowercase() ?: continue
                val kind = when {
                    Regex("msgstore.*\\.crypt\\d*").matches(n) -> Candidate.Kind.CRYPT_BACKUP
                    n.endsWith(".zip") && n.contains("whatsapp") -> Candidate.Kind.EXPORT_ZIP
                    n.endsWith(".txt") && n.contains("whatsapp") -> Candidate.Kind.EXPORT_TXT
                    else -> continue
                }
                out += Candidate(
                    label = when (kind) {
                        Candidate.Kind.CRYPT_BACKUP -> context.getString(R.string.label_crypt_backup)
                        else -> context.getString(R.string.label_chat_export)
                    },
                    uri = child.uri, file = null,
                    sizeBytes = child.length(),
                    modified = child.lastModified(),
                    kind = kind,
                )
            }
        }
    }

    /** Rooted-only: copy msgstore.db (+ media) out of the WhatsApp sandbox via su. */
    fun rootedCopyOfLiveDatabase(destDir: File): File? {
        if (!RootHelper.isRooted()) return null
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        // Stage in app-private external dir where even untrusted `su -c cp` output is readable.
        val stage = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "rootstage",
        ).apply { mkdirs() }
        val tmp = File(stage, "msgstore.db")
        val cmds = listOf(
            "rm -f ${tmp.absolutePath}",
            "cp /data/data/com.whatsapp/databases/msgstore.db ${tmp.absolutePath}",
            "chmod 666 ${tmp.absolutePath}",
        )
        if (!RootHelper.runSu(cmds) || !tmp.exists() || tmp.length() == 0L) return null
        val out = File(destDir, "msgstore-$stamp.db")
        return try {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
            if (out.exists() && out.length() > 0) out else null
        } catch (_: Exception) {
            null
        }
    }

    fun humanSize(bytes: Long): String = when {
        bytes > 999_999_999 -> String.format(Locale.US, "%.2f GB", bytes / 1e9)
        bytes > 999_999 -> String.format(Locale.US, "%.2f MB", bytes / 1e6)
        bytes > 999 -> String.format(Locale.US, "%.1f KB", bytes / 1e3)
        else -> "$bytes B"
    }

    fun humanDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(millis))
}
