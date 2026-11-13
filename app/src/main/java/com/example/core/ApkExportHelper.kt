package com.example.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale

data class AppBuildMetadata(
    val appName: String,
    val applicationId: String,
    val versionName: String,
    val versionCode: Long,
    val buildType: String,
    val apkSizeBytes: Long,
    val sourceApkPath: String
) {
    val suggestedFileName: String
        get() = "LeadFinderAI-v${versionName.ifBlank { "1.0" }}-${buildType}.apk"
}

object ApkExportHelper {
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun readAppBuildMetadata(context: Context): AppBuildMetadata {
        val pm = context.packageManager
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0)
        }
        val sourcePath = context.applicationInfo.sourceDir.orEmpty()
        val apkFile = if (sourcePath.isNotBlank()) File(sourcePath) else null
        val sizeBytes = if (apkFile != null && apkFile.exists()) apkFile.length() else 0L
        val appLabel = context.applicationInfo.loadLabel(pm)?.toString().orEmpty()

        return AppBuildMetadata(
            appName = appLabel.ifBlank { "LeadFinder AI" },
            applicationId = context.packageName,
            versionName = pkgInfo.versionName.orEmpty().ifBlank { BuildConfig.VERSION_NAME },
            versionCode = PackageInfoCompat.getLongVersionCode(pkgInfo),
            buildType = BuildConfig.BUILD_TYPE,
            apkSizeBytes = sizeBytes,
            sourceApkPath = sourcePath
        )
    }

    fun formatFileSizeMb(bytes: Long): String {
        if (bytes <= 0L) return "Unknown"
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.US, "%.2f MB", mb)
    }

    suspend fun exportApkToUri(context: Context, targetUri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = readAppBuildMetadata(context)
            val sourceFile = File(metadata.sourceApkPath)
            require(sourceFile.exists() && sourceFile.canRead()) {
                "Installed APK source file is unavailable on this device."
            }
            var copiedBytes = 0L
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                FileInputStream(sourceFile).use { input ->
                    copiedBytes = input.copyTo(output)
                }
                output.flush()
            } ?: error("Could not open destination file for writing.")
            require(copiedBytes > 0L) { "Exported APK was empty." }
            copiedBytes
        }
    }

    suspend fun prepareShareApkIntent(context: Context): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val metadata = readAppBuildMetadata(context)
            val sourceFile = File(metadata.sourceApkPath)
            require(sourceFile.exists() && sourceFile.canRead()) {
                "Installed APK source file is unavailable on this device."
            }

            val exportDir = File(context.cacheDir, "apk_exports").apply { mkdirs() }
            val exportedFile = File(exportDir, metadata.suggestedFileName)
            sourceFile.inputStream().use { input ->
                exportedFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, exportedFile)

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = APK_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "${metadata.appName} v${metadata.versionName} APK")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(sendIntent, "Share ${metadata.appName} APK")
        }
    }
}
