package com.personal.wabackup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personal.wabackup.BackupWorker
import java.util.concurrent.TimeUnit

/**
 * Safety net: if the app is force-stopped (or WorkManager's job was dropped by a
 * battery-optimization killer) but NOT uninstalled, this receiver re-arms the
 * periodic backup job as soon as the device wakes up / unlocks / boots.
 *
 * NOTE: after a real uninstall nothing inside the app can run — Android removes
 * the package entirely. Previously uploaded backups always stay in Telegram cloud.
 */
class PersistenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_USER_PRESENT ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_SCREEN_ON
        ) {
            reArmBackupJob(context)
        }
    }

    private fun reArmBackupJob(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                6, TimeUnit.HOURS,
            ).build()
            // KEEP: never disturb an already-scheduled job, only restore it if missing.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "wa-periodic-backup",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } catch (_: Exception) {
            // WorkManager not initialized yet in this process — ignore, BootReceiver covers it.
        }
    }
}
