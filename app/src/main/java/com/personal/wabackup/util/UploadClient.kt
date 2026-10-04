package com.personal.wabackup.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Uploads a backup file to the personal server.
 *
 * Flow (matches /wa-backup/server/server.py):
 *   POST {server}/api/upload  with headers:
 *     Authorization: Bearer <token>
 *     X-File-Name:   <name>
 *     X-File-Sha256: <hex>
 *     X-Encrypted:   "true"
 *   body = AES-256-CBC ciphertext of the file, key derived from the token.
 *
 * The file is ALSO end-to-end encrypted on-device with a key derived from the auth
 * token via PBKDF2 — so even if the server disk is stolen, plaintext chats never exist
 * there (server stores only ciphertext).
 */
object UploadClient {

    private const val TAG = "UploadClient"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 120_000

    suspend fun uploadFile(settings: SettingsStore, file: File, remoteName: String): Boolean =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                val sha = sha256Hex(file)
                val enc = encryptFile(file, settings.authToken) ?: return@withContext false

                conn = (URL(settings.serverUrl.trimEnd('/') + "/api/upload").openConnection()
                        as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = true
                    setRequestProperty("Authorization", "Bearer " + settings.authToken)
                    setRequestProperty("Content-Type", "application/octet-stream")
                    setRequestProperty("X-File-Name", remoteName)
                    setRequestProperty("X-File-Sha256", sha)
                    setRequestProperty("X-Encrypted", "true")
                    setChunkedStreamingMode(64 * 1024)
                }

                conn.outputStream.use { out ->
                    enc.inputStream().use { it.copyTo(out) }
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    true
                } else {
                    val err = conn.errorStream?.readBytes()?.decodeToString().orEmpty()
                    Log.w(TAG, "Upload failed HTTP $code: ${err.take(200)}")
                    false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Upload error: ${e.message}")
                false
            } finally {
                conn?.disconnect()
            }
        }

    /** Lightweight health check used by the UI ("Test connection" button). */
    suspend fun pingServer(settings: SettingsStore): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(settings.serverUrl.trimEnd('/') + "/api/health").openConnection()
                    as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Authorization", "Bearer " + settings.authToken)
            }
            conn.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    // ---- crypto --------------------------------------------------------------

    private fun sha256Hex(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun deriveKey(token: String): SecretKeySpec {
        // PBKDF2WithHmacSHA256, 120k iterations, device-salt bound to the app id.
        val salt = "wa-backup-personal-v1".toByteArray(Charsets.UTF_8)
        val skf = PBEKeySpec(token.toCharArray(), salt, 120_000, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(skf).encoded
        return SecretKeySpec(key, "AES")
    }

    /** Returns an on-disk temp file containing: [12B magic][16B IV][ciphertext]. */
    private fun encryptFile(src: File, token: String): File? {
        return try {
            val cipherFile = File.createTempFile("enc-", ".bin", src.parentFile)
            val iv = ByteArray(16).also { java.security.SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(token), IvParameterSpec(iv))
            cipherFile.outputStream().use { out ->
                out.write(MAGIC)
                out.write(iv)
                src.inputStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(cipher.update(buf, 0, n))
                    }
                    out.write(cipher.doFinal())
                }
            }
            cipherFile
        } catch (_: Exception) {
            null
        }
    }

    private val MAGIC = byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'B'.code.toByte(), 1, 0, 0)
}
