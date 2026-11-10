package com.example.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object AppShareHelper {

    fun shareApkFile(context: Context) {
        try {
            val appSourceDir = context.applicationInfo.sourceDir
            val sourceFile = File(appSourceDir)
            if (!sourceFile.exists()) {
                shareAppText(context)
                return
            }

            val cacheApk = File(context.cacheDir, "MissedCall-SOS.apk")
            sourceFile.copyTo(cacheApk, overwrite = true)

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheApk
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "MissedCall SOS - Emergency Repeated Call Guard by RAAJAN\nInstall this app to never miss urgent repeated calls!"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share MissedCall SOS APK"))
        } catch (e: Exception) {
            shareAppText(context)
        }
    }

    fun shareAppText(context: Context) {
        val shareText = "Stay safe with MissedCall SOS - Emergency Repeated Call Guard by RAAJAN!\n\n" +
                "• Piercing siren on repeated urgent missed calls\n" +
                "• Overrides Silent and DND modes\n" +
                "• Animated full-screen lockscreen alarm\n" +
                "• Offline emergency SMS alert\n" +
                "• 100% private & on-device\n\n" +
                "GitHub: https://github.com/RAJAN00005/MissedCallSOS"

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "MissedCall SOS App")
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share MissedCall SOS")
        context.startActivity(shareIntent)
    }
}
