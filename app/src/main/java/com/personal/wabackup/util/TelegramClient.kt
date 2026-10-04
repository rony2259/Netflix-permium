package com.personal.wabackup.util

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Sends WhatsApp backup files straight to your Telegram chat via the bot HTTP
 * API (https://core.telegram.org/bots/api). No server needed for this path.
 *
 * Limits handled:
 *  - Bot sendDocument max is 50 MB -> larger files are split into parts and
 *    each part sent separately (rejoin with `cat` on the PC if needed).
 *  - Files are uploaded RAW (your WhatsApp .crypt14 etc. is already end-to-end
 *    encrypted by WhatsApp itself; a crypt## file is useless without your
 *    WhatsApp backup password + key file, which never leave the phone).
 */
object TelegramClient {

    private const val TAG = "TelegramClient"
    private const val TG_LIMIT = 48L * 1024 * 1024 // stay under 50 MB

    fun isConfigured(settings: SettingsStore): Boolean =
        settings.tgAutoEnabled && settings.tgBotToken.isNotBlank() &&
            settings.tgChatId.isNotBlank()

    /** True once the chat id has been resolved (not the "auto" placeholder). */
    fun chatIdResolved(settings: SettingsStore): Boolean =
        settings.tgChatId.isNotBlank() && settings.tgChatId != "auto"

    /**
     * If chat id is "auto", discover it via getUpdates (the user must have sent
     * at least one message like /start to the bot). Returns true if a usable
     * chat id is available afterwards.
     */
    fun ensureChatId(settings: SettingsStore): Boolean {
        if (settings.tgChatId != "auto") return settings.tgChatId.isNotBlank()
        try {
            val url = URL("https://api.telegram.org/bot${settings.tgBotToken}/getUpdates")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            if (conn.responseCode == 200) {
                val resp = conn.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
                val arr = JSONObject(resp).optJSONArray("result") ?: JSONArray()
                for (i in arr.length() - 1 downTo 0) { // newest update first
                    val chat = arr.optJSONObject(i)
                        ?.optJSONObject("message")?.optJSONObject("chat")
                        ?: arr.optJSONObject(i)?.optJSONObject("channel_post")?.optJSONObject("chat")
                        ?: arr.optJSONObject(i)?.optJSONObject("my_chat_member")
                            ?.optJSONObject("chat")
                    val id = chat?.optString("id")?.takeIf { it.isNotEmpty() } ?: continue
                    settings.tgChatId = id
                    Log.i(TAG, "Auto-detected telegram chat id: $id")
                    return true
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "ensureChatId failed: ${e.message}")
        }
        return false
    }

    /** Returns true if everything was delivered to Telegram. */
    fun sendFile(settings: SettingsStore, src: File, captionBase: String): Boolean {
        val bot = settings.tgBotToken
        val chat = settings.tgChatId
        val size = src.length()

        if (size <= TG_LIMIT) {
            return sendDocument(bot, chat, src, "$captionBase (${human(size)})")
        }

        val n = ((size + TG_LIMIT - 1) / TG_LIMIT).toInt()
        var allOk = true
        src.inputStream().use { input ->
            for (i in 0 until n) {
                val partLen = minOf(TG_LIMIT, size - i * TG_LIMIT).toInt()
                val buf = ByteArray(partLen)
                var read = 0
                while (read < partLen) {
                    val r = input.read(buf, read, partLen - read)
                    if (r < 0) break
                    read += r
                }
                val part = File(src.parentFile, "${src.name}.part${i + 1}of$n")
                try {
                    part.writeBytes(buf.copyOf(read))
                    if (!sendDocument(bot, chat, part,
                            "$captionBase [${i + 1}/$n]")) allOk = false
                } finally {
                    part.delete()
                }
            }
        }
        return allOk
    }

    fun sendMessage(settings: SettingsStore, text: String) {
        sendMessageQuiet(settings, text)
    }

    /** Returns true if the message was delivered. */
    fun sendMessageQuiet(settings: SettingsStore, text: String): Boolean {
        val chat = settings.tgChatId
        if (chat.isBlank() || chat == "auto") return false
        return try {
            val url = URL("https://api.telegram.org/bot${settings.tgBotToken}/sendMessage")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            val body = ("chat_id=" + java.net.URLEncoder.encode(chat, "UTF-8") +
                    "&text=" + java.net.URLEncoder.encode(text, "UTF-8")).toByteArray(StandardCharsets.UTF_8)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.use { it.readBytes().toString(StandardCharsets.UTF_8) } ?: ""
            val ok = code == 200 && JSONObject(resp).optBoolean("ok", false)
            if (!ok) Log.w(TAG, "sendMessage $code: ${resp.take(200)}")
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.w(TAG, "sendMessage failed: ${e.message}")
            false
        }
    }

    private fun sendDocument(bot: String, chat: String, file: File, caption: String): Boolean {
        val boundary = "----wab${System.nanoTime()}"
        return try {
            val conn = URL("https://api.telegram.org/bot$bot/sendDocument")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 30_000
            conn.readTimeout = 300_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { out ->
                writeField(out, boundary, "chat_id", chat)
                writeField(out, boundary, "caption", caption)
                out.write("--$boundary\r\n".toByteArray())
                out.write(("Content-Disposition: form-data; name=\"document\"; " +
                        "filename=\"${file.name}\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n").toByteArray())
                file.inputStream().use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = conn.responseCode
            val resp = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.use { it.readBytes().toString(StandardCharsets.UTF_8) } ?: ""
            val ok = code == 200 && JSONObject(resp).optBoolean("ok", false)
            if (!ok) Log.w(TAG, "sendDocument failed ($code): ${resp.take(300)}")
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.w(TAG, "sendDocument error: ${e.message}")
            false
        }
    }

    private fun writeField(out: java.io.OutputStream, boundary: String, name: String, value: String) {
        out.write("--$boundary\r\n".toByteArray())
        out.write(("Content-Disposition: form-data; name=\"$name\"\r\n\r\n").toByteArray())
        out.write(value.toByteArray(StandardCharsets.UTF_8))
        out.write("\r\n".toByteArray())
    }

    private fun readErr(conn: HttpURLConnection): String = try {
        conn.errorStream?.use { it.readBytes().toString(StandardCharsets.UTF_8).take(200) } ?: ""
    } catch (_: Exception) { "" }

    private fun human(b: Long): String = when {
        b >= 1 shl 20 -> String.format("%.1f MB", b / 1048576.0)
        b >= 1 shl 10 -> String.format("%.1f KB", b / 1024.0)
        else -> "$b B"
    }
}
