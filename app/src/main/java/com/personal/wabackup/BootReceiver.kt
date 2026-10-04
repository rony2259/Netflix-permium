package com.personal.wabackup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personal.wabackup.util.BackupScheduler
import java.util.concurrent.TimeUnit

/** Re-arm the periodic backup job after device reboot. */
class BootReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: android.content.Intent) {
        if (intent.action == android.content.Intent.ACTION_BOOT_COMPLETED) {
            BackupScheduler.schedule(context, WorkManager.getInstance(context))
        }
    }
}
