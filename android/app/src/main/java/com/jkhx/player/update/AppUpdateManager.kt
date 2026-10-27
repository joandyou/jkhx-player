package com.jkhx.player.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val title: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val publishedAt: String
)

sealed interface UpdateCheckResult {
    data object Idle : UpdateCheckResult
    data object Checking : UpdateCheckResult
    data class UpdateAvailable(val info: AppReleaseInfo, val currentVersion: String) : UpdateCheckResult
    data class UpToDate(val currentVersion: String) : UpdateCheckResult
    data class Error(val message: String) : UpdateCheckResult
}

sealed interface DownloadProgress {
    data object Idle : DownloadProgress
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long, val percent: Int) : DownloadProgress
    data class Success(val apkFile: File) : DownloadProgress
    data class Failure(val error: String) : DownloadProgress
}

class AppUpdateManager private constructor(private val appContext: Context) {

    companion object {
        const val GITHUB_OWNER = "joandyou"
        const val GITHUB_REPO = "jkhx-player"
        private const val API_RELEASES_LATEST = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

        @Volatile
        private var INSTANCE: AppUpdateManager? = null

        fun getInstance(context: Context): AppUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppUpdateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _checkState = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    val checkState: StateFlow<UpdateCheckResult> = _checkState.asStateFlow()

    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress.asStateFlow()

    val currentVersionName: String
        get() = try {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }

    val currentVersionCode: Long
        get() = try {
            val pInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            0L
        }

    fun resetCheckState() {
        _checkState.value = UpdateCheckResult.Idle
    }

    fun resetDownloadProgress() {
        _downloadProgress.value = DownloadProgress.Idle
    }

    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        _checkState.value = UpdateCheckResult.Checking
        try {
            val request = Request.Builder()
                .url(API_RELEASES_LATEST)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "JKHX-Player-App/${currentVersionName}")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    val result = UpdateCheckResult.UpToDate(currentVersionName)
                    _checkState.value = result
                    return@withContext result
                }

                if (!response.isSuccessful) {
                    val result = UpdateCheckResult.Error("HTTP ${response.code}: ${response.message}")
                    _checkState.value = result
                    return@withContext result
                }

                val bodyStr = response.body?.string().orEmpty()
                if (bodyStr.isBlank()) {
                    val result = UpdateCheckResult.Error("Phản hồi rỗng từ GitHub")
                    _checkState.value = result
                    return@withContext result
                }

                val json = JSONObject(bodyStr)
                val tagName = json.optString("tag_name", "").trim()
                val title = json.optString("name", tagName)
                val body = json.optString("body", "").trim()
                val publishedAt = json.optString("published_at", "")

                val assets = json.optJSONArray("assets")
                var apkUrl: String? = null
                var apkFileName: String? = null
                var apkSize: Long = 0L

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = asset.optString("browser_download_url")
                            apkFileName = name
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                if (apkUrl.isNullOrBlank()) {
                    val result = UpdateCheckResult.UpToDate(currentVersionName)
                    _checkState.value = result
                    return@withContext result
                }

                val remoteVersionClean = tagName.removePrefix("v").removePrefix("V").trim()
                val currentVersionClean = currentVersionName.removePrefix("v").removePrefix("V").trim()

                if (isRemoteVersionNewer(remoteVersionClean, currentVersionClean)) {
                    val releaseInfo = AppReleaseInfo(
                        tagName = tagName,
                        versionName = remoteVersionClean,
                        title = title.ifBlank { "Bản cập nhật v$remoteVersionClean" },
                        releaseNotes = body.ifBlank { "Bản cập nhật tối ưu hiệu năng và sửa lỗi." },
                        apkDownloadUrl = apkUrl,
                        apkFileName = apkFileName ?: "jkhx-player-v$remoteVersionClean.apk",
                        apkSizeBytes = apkSize,
                        publishedAt = publishedAt
                    )
                    val result = UpdateCheckResult.UpdateAvailable(releaseInfo, currentVersionClean)
                    _checkState.value = result
                    return@withContext result
                } else {
                    val result = UpdateCheckResult.UpToDate(currentVersionClean)
                    _checkState.value = result
                    return@withContext result
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val result = UpdateCheckResult.Error(e.message ?: "Không thể kết nối đến máy chủ GitHub")
            _checkState.value = result
            result
        }
    }

    suspend fun downloadAndInstall(
        releaseInfo: AppReleaseInfo,
        onProgress: ((bytesDownloaded: Long, totalBytes: Long, percent: Int) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            _downloadProgress.value = DownloadProgress.Downloading(0, releaseInfo.apkSizeBytes, 0)
            val updateDir = File(appContext.cacheDir, "apk_updates").apply { mkdirs() }
            val targetApk = File(updateDir, releaseInfo.apkFileName)

            if (targetApk.exists()) {
                targetApk.delete()
            }

            val request = Request.Builder()
                .url(releaseInfo.apkDownloadUrl)
                .header("User-Agent", "JKHX-Player-App/${currentVersionName}")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = "Tải thất bại (HTTP ${response.code})"
                    _downloadProgress.value = DownloadProgress.Failure(err)
                    return@withContext Result.failure(Exception(err))
                }

                val body = response.body ?: run {
                    val err = "Nội dung tải về rỗng"
                    _downloadProgress.value = DownloadProgress.Failure(err)
                    return@withContext Result.failure(Exception(err))
                }

                val totalLength = if (body.contentLength() > 0) body.contentLength() else releaseInfo.apkSizeBytes
                var downloaded = 0L

                body.byteStream().use { input ->
                    FileOutputStream(targetApk).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytes: Int
                        var lastPercent = -1

                        while (input.read(buffer).also { bytes = it } != -1) {
                            output.write(buffer, 0, bytes)
                            downloaded += bytes

                            val percent = if (totalLength > 0) {
                                ((downloaded * 100) / totalLength).toInt().coerceIn(0, 100)
                            } else 0

                            if (percent != lastPercent) {
                                lastPercent = percent
                                _downloadProgress.value = DownloadProgress.Downloading(downloaded, totalLength, percent)
                                onProgress?.invoke(downloaded, totalLength, percent)
                            }
                        }
                        output.flush()
                    }
                }
            }

            _downloadProgress.value = DownloadProgress.Success(targetApk)
            Result.success(targetApk)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message ?: "Lỗi khi tải tập tin cập nhật"
            _downloadProgress.value = DownloadProgress.Failure(msg)
            Result.failure(e)
        }
    }

    fun launchInstall(context: Context, apkFile: File) {
        if (!apkFile.exists()) return

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    fun isRemoteVersionNewer(remoteVersion: String, currentVersion: String): Boolean {
        val remoteParts = parseVersionParts(remoteVersion)
        val currentParts = parseVersionParts(currentVersion)

        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    private fun parseVersionParts(ver: String): List<Int> {
        val digitsOnly = ver.substringBefore("-").substringBefore("+")
        return digitsOnly.split(".")
            .mapNotNull { it.trim().toIntOrNull() }
    }
}

