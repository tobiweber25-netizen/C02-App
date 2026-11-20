package com.aquarium.co2tracker.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.aquarium.co2tracker.BuildConfig
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class AppUpdateManager(
    private val context: Context,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val githubToken: String = BuildConfig.GITHUB_TOKEN
) {

    private val gson = Gson()

    suspend fun checkForUpdates(
        owner: String = BuildConfig.GITHUB_REPO_OWNER,
        repo: String = BuildConfig.GITHUB_REPO_NAME
    ): UpdateCheckState = withContext(Dispatchers.IO) {
        if (owner.isBlank() || repo.isBlank()) {
            return@withContext UpdateCheckState.Error("GitHub repository owner or name not configured.")
        }

        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        val headers = mapOf(
            "Accept" to "application/vnd.github.v3+json",
            "User-Agent" to "AquaCO2-Android"
        )

        try {
            executeWithPublicFallback(url = url, headers = headers).use { response ->
                if (!response.isSuccessful) {
                    return@withContext if (response.code == 404) {
                        UpdateCheckState.UpToDate(BuildConfig.VERSION_NAME)
                    } else {
                        UpdateCheckState.Error("GitHub API returned error ${response.code}: ${response.message}")
                    }
                }

                val bodyString = response.body?.string().orEmpty()
                if (bodyString.isBlank()) {
                    return@withContext UpdateCheckState.Error("Empty response from GitHub Releases.")
                }

                val release = gson.fromJson(bodyString, GitHubRelease::class.java)
                val currentVersion = BuildConfig.VERSION_NAME
                val isNewer = isNewerVersion(release.tagName, currentVersion)

                if (!isNewer) {
                    return@withContext UpdateCheckState.UpToDate(currentVersion)
                }

                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                if (apkAsset == null) {
                    return@withContext UpdateCheckState.Error("Release ${release.tagName} found, but no .apk asset attached.")
                }

                UpdateCheckState.UpdateAvailable(
                    currentVersion = currentVersion,
                    newVersion = release.tagName.removePrefix("v").removePrefix("V"),
                    releaseTitle = release.name ?: release.tagName,
                    releaseNotes = release.body.orEmpty(),
                    downloadUrl = apkAsset.downloadUrl,
                    assetSize = apkAsset.size
                )
            }
        } catch (e: Exception) {
            UpdateCheckState.Error(e.message ?: "Failed to check for updates.")
        }
    }

    suspend fun downloadApk(
        downloadUrl: String,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val headers = mapOf(
                "Accept" to "application/octet-stream",
                "User-Agent" to "AquaCO2-Android"
            )

            executeWithPublicFallback(url = downloadUrl, headers = headers).use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Failed to download APK: HTTP ${response.code}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Response body is empty"))
                val totalBytes = body.contentLength()

                val updatesDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
                val destinationFile = File(updatesDir, "update.apk")
                if (destinationFile.exists()) destinationFile.delete()

                body.byteStream().use { inputStream ->
                    FileOutputStream(destinationFile).use { outputStream ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalRead = 0L
                        var lastReportMillis = 0L

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            val now = System.currentTimeMillis()
                            if (now - lastReportMillis >= 100L || (totalBytes > 0 && totalRead >= totalBytes)) {
                                lastReportMillis = now
                                val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                                withContext(Dispatchers.Main) {
                                    onProgress(progress, totalRead, totalBytes)
                                }
                            }
                        }
                        outputStream.flush()
                    }
                }

                Result.success(destinationFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes a GitHub request using [githubToken] if present, and transparently retries
     * unauthenticated if GitHub rejects an expired/revoked token with HTTP 401 on a public repo.
     */
    internal fun executeWithPublicFallback(
        url: String,
        headers: Map<String, String>
    ): Response = executeWithPublicFallback(
        httpClient = httpClient,
        url = url,
        headers = headers,
        githubToken = githubToken
    )

    fun canInstallUnknownApps(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openUnknownAppInstallSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val apkUri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        private fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()

        internal fun executeWithPublicFallback(
            httpClient: OkHttpClient,
            url: String,
            headers: Map<String, String>,
            githubToken: String
        ): Response {
            val hasToken = githubToken.isNotBlank()
            val initialRequest = buildRequest(url, headers, githubToken, includeAuth = hasToken)
            val initialResponse = httpClient.newCall(initialRequest).execute()

            if (initialResponse.code == 401 && hasToken) {
                initialResponse.close()
                val publicRequest = buildRequest(url, headers, githubToken, includeAuth = false)
                return httpClient.newCall(publicRequest).execute()
            }

            return initialResponse
        }

        private fun buildRequest(
            url: String,
            headers: Map<String, String>,
            githubToken: String,
            includeAuth: Boolean
        ): Request {
            val builder = Request.Builder().url(url)
            headers.forEach { (name, value) -> builder.header(name, value) }
            if (includeAuth && githubToken.isNotBlank()) {
                builder.header("Authorization", "Bearer $githubToken")
            }
            return builder.build()
        }

        internal fun isNewerVersion(remoteTag: String, currentVersion: String): Boolean {
            val cleanRemote = remoteTag.trim().removePrefix("v").removePrefix("V")
            val cleanCurrent = currentVersion.trim().removePrefix("v").removePrefix("V")

            val remoteParts = cleanRemote.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }
            val currentParts = cleanCurrent.split(".").mapNotNull { it.takeWhile { ch -> ch.isDigit() }.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
            return false
        }
    }
}
