package org.jellyfin.androidtv.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.androidtv.R
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class AppUpdater(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val CHANNEL_ID = "app_updater_channel"
    private val NOTIFICATION_ID = 1
    private val GITHUB_RELEASES_URL = "https://api.github.com/repos/Sam42a/DUNE/releases/latest"
    private val APK_MIME_TYPE = "application/vnd.android.package-archive"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.app_updater_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.app_updater_channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String, progress: Int = -1, indeterminate: Boolean = false) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(progress != -1)
            .setOnlyAlertOnce(true)

        if (progress >= 0) {
            builder.setProgress(100, progress, indeterminate)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun dismissNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    suspend fun checkForUpdates(currentVersion: String): UpdateResult {
        return try {
            val request = Request.Builder()
                .url(GITHUB_RELEASES_URL)
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (!response.isSuccessful) {
                return UpdateResult.Error("Failed to check for updates: ${response.code}")
            }

            val json = response.body?.string() ?: return UpdateResult.Error("Empty response from server")
            val release = JSONObject(json)
            val latestVersion = release.getString("tag_name").trimStart('v')
            val assets = release.getJSONArray("assets")

            // Find the APK asset
            var downloadUrl = ""
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("content_type") == "application/vnd.android.package-archive" ||
                    asset.getString("name").endsWith(".apk")) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }

            if (downloadUrl.isEmpty()) {
                return UpdateResult.Error("No APK found in the latest release")
            }

            if (compareVersions(currentVersion, latestVersion) >= 0) {
                return UpdateResult.NoUpdateAvailable
            }

            UpdateResult.UpdateAvailable(latestVersion, downloadUrl)
        } catch (e: Exception) {
            UpdateResult.Error("Error checking for updates: ${e.message}")
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val v1 = version1.split(".").map { it.toInt() }
        val v2 = version2.split(".").map { it.toInt() }

        for (i in 0 until maxOf(v1.size, v2.size)) {
            val num1 = if (i < v1.size) v1[i] else 0
            val num2 = if (i < v2.size) v2[i] else 0

            if (num1 < num2) return -1
            if (num1 > num2) return 1
        }

        return 0
    }

    suspend fun downloadAndInstall(version: String, downloadUrl: String) {
		Timber.tag("AppUpdater").d("Starting downloadAndInstall for version: $version")
		Timber.tag("AppUpdater").d("Download URL: $downloadUrl")
        try {
            showNotification(
                context.getString(R.string.update_downloading_title),
                context.getString(R.string.update_downloading_message, version),
                0,
                true
            )
			Timber.tag("AppUpdater").d("Notification shown")

			Timber.tag("AppUpdater").d("Creating download request")
            val request = Request.Builder()
                .url(downloadUrl)
                .build()
			Timber.tag("AppUpdater").d("Request created")

            val response = try {
                Timber.tag("AppUpdater").d("Executing network request...")
				withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }
            } catch (e: Exception) {
				Timber.tag("AppUpdater").e(e, "Network request failed")
                throw IOException("Network request failed: ${e.message}", e)
            }

			Timber.tag("AppUpdater").d("Response code: ${response.code}")
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
				Timber.tag("AppUpdater").e("Failed to download update: ${response.code} - ${response.message}\n$errorBody")
                throw IOException("Failed to download update: ${response.code} - ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength()
			Timber.tag("AppUpdater").d("Content length: $contentLength bytes")
            val inputStream = body.byteStream()

            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IOException("Cannot access downloads directory")

            val apkFile = File(downloadsDir, "DUNE_${version}.apk")
			Timber.tag("AppUpdater").d("Saving to: ${apkFile.absolutePath}")
            val outputStream = FileOutputStream(apkFile)

            val buffer = ByteArray(4096)
            var downloaded: Long = 0
            var lastProgress = -1

            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break

                outputStream.write(buffer, 0, read)
                downloaded += read.toLong()

                if (contentLength > 0) {
                    val progress = (downloaded * 100 / contentLength).toInt()
                    if (progress > lastProgress) {
                        lastProgress = progress
                        showNotification(
                            context.getString(R.string.update_downloading_title),
                            context.getString(R.string.update_downloading_message, version),
                            progress
                        )
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            Timber.tag("AppUpdater").d("Download complete, starting installation")
			Timber.tag("AppUpdater").d("File exists: ${apkFile.exists()}, size: ${apkFile.length()} bytes")
            installApk(apkFile)

        } catch (e: Exception) {
            showNotification(
                context.getString(R.string.update_error_title),
                e.message ?: "Unknown error",
                -1
            )
            throw e
        } finally {
            // Dismiss notification after a delay
            kotlinx.coroutines.delay(3000)
            dismissNotification()
        }
    }

    private fun installApk(apkFile: File) {
        Timber.d("installApk called with file: ${apkFile.absolutePath}")
        Timber.d("File exists: ${apkFile.exists()}, size: ${apkFile.length()} bytes")

        try {
            // Verify file exists and is readable
            if (!apkFile.exists() || !apkFile.canRead()) {
                throw IllegalStateException("APK file doesn't exist or is not readable")
            }

            // Get the FileProvider URI
            val authority = "${context.packageName}.fileprovider"
            Timber.d("Using authority: $authority")

            val apkUri = try {
                FileProvider.getUriForFile(context, authority, apkFile).also {
                    Timber.d("Created content URI: $it")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error creating content URI")
                throw IllegalStateException("Could not create content URI: ${e.message}")
            }

            // Create the install intent
            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(apkUri, APK_MIME_TYPE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                // Log all activities that can handle this intent
                val resolveInfoList = context.packageManager
                    .queryIntentActivities(this, PackageManager.MATCH_DEFAULT_ONLY) ?: emptyList()

                Timber.d("Found ${resolveInfoList.size} activities that can handle the installation")

                for (resolveInfo in resolveInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    val className = resolveInfo.activityInfo.name
                    Timber.d("  - $packageName/$className")

                    // Grant temporary read permission to the package
                    context.grantUriPermission(
                        packageName,
                        apkUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    Timber.d("Granted read permission to: $packageName")
                }

                if (resolveInfoList.isEmpty()) {
                    Timber.e("No activities found to handle the installation!")
                }
            }

            // Add FLAG_ACTIVITY_CLEAR_TOP to ensure we get a fresh instance
            installIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            // Log the intent details
            Timber.d("Install intent: $installIntent")
            Timber.d("  Action: ${installIntent.action}")
            Timber.d("  Data: ${installIntent.data}")
            Timber.d("  Type: ${installIntent.type}")
            Timber.d("  Flags: ${installIntent.flags.toUInt().toString(16)}")

            // Try to start the installation
            try {
                Timber.d("Starting installation activity...")
                context.startActivity(installIntent)
                Timber.d("Installation activity started successfully")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start installation activity")
                throw e
            }

        } catch (e: Exception) {
            val errorMsg = "Error starting installation: ${e.message}"
            Timber.e(e, errorMsg)

            // Show error to user on main thread
            Handler(Looper.getMainLooper()).post {
                try {
                    val errorMessage = context.getString(R.string.install_error, e.localizedMessage ?: "Unknown error")
                    Timber.e("Installation error: $errorMessage")
                    Toast.makeText(
                        context,
                        errorMessage,
                        Toast.LENGTH_LONG
                    ).show()
                } catch (toastError: Exception) {
                    Timber.e(toastError, "Error showing error toast")
                }
            }
        }
    }
}

// UpdateResult class has been moved to its own file for better organization
