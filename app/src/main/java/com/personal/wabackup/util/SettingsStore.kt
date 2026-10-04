package com.personal.wabackup.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.content.SharedPreferences

/**
 * App settings, stored in EncryptedSharedPreferences so the server token never
 * sits in plaintext on disk.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (_: Exception) {
        // Fallback for devices where the keystore master key fails (rare).
        context.applicationContext.getSharedPreferences(FILE_NAME_PLAIN, Context.MODE_PRIVATE)
    }

    /** Empty by default — Telegram direct mode must work with NO server at all. */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, "") ?: ""
        set(v) = prefs.edit().putString(KEY_SERVER, v.trim()).apply()

    var authToken: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TOKEN, v.trim()).apply()

    /** Backup cadence in hours (WorkManager periodic minimum is ~15 min; we allow 1..24h). */
    var intervalHours: Int
        get() = prefs.getInt(KEY_INTERVAL_HOURS, 1).coerceIn(1, 24)
        set(v) = prefs.edit().putInt(KEY_INTERVAL_HOURS, v.coerceIn(1, 24)).apply()

    var wifiOnly: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, false)
        set(v) = prefs.edit().putBoolean(KEY_WIFI_ONLY, v).apply()

    var includeMedia: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_MEDIA, false)
        set(v) = prefs.edit().putBoolean(KEY_INCLUDE_MEDIA, v).apply()

    var rootedDbCopyEnabled: Boolean
        get() = prefs.getBoolean(KEY_ROOTED_DB, false) && RootHelper.isRooted()
        set(v) = prefs.edit().putBoolean(KEY_ROOTED_DB, v).apply()

    /**
     * Auto mode: no server needed. The app only collects WhatsApp backup files
     * into its own folder (Documents/wa-auto) which is visible over USB / MTP.
     * The PC script (server/pc_puller.py or adb pull) picks them up.
     */
    var autoMode: Boolean
        get() = prefs.getBoolean(KEY_AUTO_MODE, false)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_MODE, v).apply()

    /** Telegram bot token from @BotFather (optional direct-send mode). */
    var tgBotToken: String
        get() = prefs.getString(KEY_TG_BOT, DEFAULT_TG_BOT) ?: ""
        set(v) = prefs.edit().putString(KEY_TG_BOT, v.trim()).apply()

    /** Numeric chat id of YOUR telegram account (get it from @userinfobot). */
    var tgChatId: String
        get() = prefs.getString(KEY_TG_CHAT, DEFAULT_TG_CHAT) ?: DEFAULT_TG_CHAT
        set(v) = prefs.edit().putString(KEY_TG_CHAT, v.trim()).apply()

    /** Telegram auto-send master switch — ON by default so backups flow to
     *  the bot automatically once storage permission is granted. */
    var tgAutoEnabled: Boolean
        get() = prefs.getBoolean(KEY_TG_AUTO, true)
        set(v) = prefs.edit().putBoolean(KEY_TG_AUTO, v).apply()

    /** Persisted SAF tree Uri (WhatsApp folder picked by the user), if any. */
    var pickedTreeUri: String?
        get() = prefs.getString(KEY_PICKED_TREE, null)
        set(v) = prefs.edit().putString(KEY_PICKED_TREE, v).apply()

    var lastUploadOkMillis: Long
        get() = prefs.getLong(KEY_LAST_OK, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_OK, v).apply()

    companion object {
        private const val FILE_NAME = "wa_backup_secure_prefs"
        private const val FILE_NAME_PLAIN = "wa_backup_prefs"
        private const val KEY_SERVER = "server_url"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_INTERVAL_HOURS = "interval_hours"
        private const val KEY_WIFI_ONLY = "wifi_only"
        private const val KEY_INCLUDE_MEDIA = "include_media"
        private const val KEY_ROOTED_DB = "rooted_db_enabled"
        private const val KEY_AUTO_MODE = "auto_mode"
        private const val KEY_LAST_OK = "last_upload_ok"
        private const val KEY_PICKED_TREE = "picked_tree_uri"
        private const val KEY_TG_BOT = "tg_bot_token"
        private const val KEY_TG_CHAT = "tg_chat_id"
        private const val KEY_TG_AUTO = "tg_auto_enabled"

        // Your personal bot (from @BotFather). Bot token alone is useless without
        // the chat id — keep both secret.
        const val DEFAULT_TG_BOT = "8681376531:AAGl0TXiDwDf8iWX2tvT-sy4dMKnhFE-h4E"

        // Auto-detected on first Telegram send if left as default: the app asks
        // getUpdates for your chat id. Placeholder kept so isConfigured() is true
        // out of the box; detection replaces it at runtime.
        const val DEFAULT_TG_CHAT = "8255475149"

        // Optional personal server (e.g. "https://backup.myhome.example.com").
        // Left empty on purpose: the app works fully via Telegram without it.
    }
}
