package com.personal.wabackup.util

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personal.wabackup.BackupWorker
import java.util.concurrent.TimeUnit

/** Registers / updates the periodic backup job. */
object BackupScheduler {

    const val WORK_NAME = "wa-periodic-backup"

    fun schedule(context: Context, wm: WorkManager) {
        val settings = SettingsStore(context)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<BackupWorker>(
            settings.intervalHours.toLong(), TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(wm: WorkManager) {
        wm.cancelUniqueWork(WORK_NAME)
    }

    /** One-off immediate run ("Backup now" button). */
    fun runNow(wm: WorkManager) {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        wm.enqueue(request)
    }
}
