package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object AppShareHelper {

    const val LATEST_APK_DOWNLOAD_URL =
        "https://github.com/RAJAN00005/MissedCallSOS/releases/latest/download/MissedCall-SOS.apk"
    const val RELEASES_PAGE_URL =
        "https://github.com/RAJAN00005/MissedCallSOS/releases"

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isSplitApk(context: Context): Boolean {
        return !context.applicationInfo.splitSourceDirs.isNullOrEmpty()
    }

    fun isDebuggableBuild(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun shareApkFile(context: Context) {
        val appContext = context.applicationContext

        if (isSplitApk(appContext)) {
            Toast.makeText(
                appContext,
                "Split APK detected — sharing the universal Release download link instead.",
                Toast.LENGTH_LONG
            ).show()
            shareDownloadLink(context)
            return
        }

        val appSourceDir = appContext.applicationInfo.sourceDir
        if (appSourceDir.isNullOrBlank()) {
            shareDownloadLink(context)
            return
        }

        Toast.makeText(appContext, "Preparing APK file for sharing…", Toast.LENGTH_SHORT).show()

        ioScope.launch {
            try {
                val sourceFile = File(appSourceDir)
                if (!sourceFile.exists() || sourceFile.length() <= 0L) {
                    withContext(Dispatchers.Main) {
                        shareDownloadLink(context)
                    }
                    return@launch
                }

                val sharedDir = File(appContext.filesDir, "shared_apk").apply { mkdirs() }
                val destApk = File(sharedDir, "MissedCall-SOS.apk")

                // Copy atomically via a temporary file so recipients never receive a truncated APK
                val tempApk = File(sharedDir, "MissedCall-SOS.apk.tmp")
                sourceFile.inputStream().use { input ->
                    tempApk.outputStream().use { output ->
                        input.copyTo(output, bufferSize = 64 * 1024)
                        output.flush()
                    }
                }

                if (tempApk.length() != sourceFile.length()) {
                    tempApk.delete()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            appContext,
                            "APK copy incomplete — sharing download link instead.",
                            Toast.LENGTH_SHORT
                        ).show()
                        shareDownloadLink(context)
                    }
                    return@launch
                }

                if (destApk.exists()) {
                    destApk.delete()
                }
                if (!tempApk.renameTo(destApk)) {
                    tempApk.copyTo(destApk, overwrite = true)
                    tempApk.delete()
                }

                val apkUri = FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    destApk
                )

                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.android.package-archive"
                    putExtra(Intent.EXTRA_STREAM, apkUri)
                    putExtra(Intent.EXTRA_SUBJECT, "MissedCall-SOS.apk")
                    clipData = ClipData.newRawUri("MissedCall-SOS.apk", apkUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                val chooser = Intent.createChooser(sendIntent, "Share MissedCall SOS APK").apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(chooser)
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        "Could not export APK directly — sharing download link instead.",
                        Toast.LENGTH_SHORT
                    ).show()
                    shareDownloadLink(context)
                }
            }
        }
    }

    fun shareDownloadLink(context: Context) {
        val shareText = "Download MissedCall SOS (Emergency Repeated Call Guard):\n\n" +
            "Direct APK Download:\n$LATEST_APK_DOWNLOAD_URL\n\n" +
            "All Releases:\n$RELEASES_PAGE_URL\n\n" +
            "Tap the APK after downloading and allow \"Install unknown apps\" if asked."

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Download MissedCall SOS APK")
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Share Download Link").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareAppText(context: Context) {
        val shareText = "Stay safe with MissedCall SOS - Emergency Repeated Call Guard by RAAJAN!\n\n" +
            "• Piercing siren on repeated urgent missed calls\n" +
            "• Overrides Silent and DND modes\n" +
            "• Full-screen lockscreen alarm even when app is closed\n" +
            "• Offline emergency SMS alert\n" +
            "• 100% private & on-device\n\n" +
            "Download APK: $LATEST_APK_DOWNLOAD_URL\n" +
            "GitHub: https://github.com/RAJAN00005/MissedCallSOS"

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "MissedCall SOS App")
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Share MissedCall SOS").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
