package com.roommate.expenses

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String
)

object AppUpdater {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/gulshanshane108-dev/RoomMateExpenses%2E/releases/latest"

    fun check(): AppUpdate? {
        val connection = URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "RoomMateExpenses")
        try {
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(body)
            val notes = release.optString("body")

            val versionCode = Regex("""versionCode\s*=\s*(\d+)""")
                .find(notes)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?: return null
            if (versionCode <= BuildConfig.VERSION_CODE) return null

            val sha256 = Regex("""sha256\s*=\s*([0-9a-fA-F]{64})""")
                .find(notes)
                ?.groupValues
                ?.get(1)
                ?.lowercase()
                ?: return null

            val versionName = release.optString("tag_name").removePrefix("v").ifBlank { "new version" }
            val apkUrl = release.optJSONArray("assets")?.let { assets ->
                (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
                    .firstOrNull { it.optString("name") == "app-release.apk" }
                    ?.optString("browser_download_url")
                    ?.takeIf { it.isNotBlank() }
            } ?: return null
            return AppUpdate(versionCode, versionName, apkUrl, sha256)
        } finally {
            connection.disconnect()
        }
    }

    fun download(context: Context, apkUrl: String): File {
        val file = File(context.cacheDir, "app-release-update.apk")
        if (file.exists()) file.delete()
        val connection = URL(apkUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "RoomMateExpenses")
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Update download failed (HTTP ${connection.responseCode})")
            }
            connection.inputStream.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        return file
    }

    fun verify(file: File, expectedSha256: String): Boolean {
        if (!file.exists()) return false
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return hex.equals(expectedSha256, ignoreCase = true)
    }

    fun install(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
