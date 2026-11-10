package com.example.util

import com.example.BuildConfig

object DeveloperInfo {
    val developerName: String = BuildConfig.DEVELOPER_NAME.trim()
    val contactEmail: String = BuildConfig.CONTACT_EMAIL.trim()
    val websiteUrl: String = BuildConfig.WEBSITE_URL.trim()
    val privacyPolicyUrl: String = BuildConfig.PRIVACY_POLICY_URL.trim()
    val downloadUrl: String = BuildConfig.DOWNLOAD_URL.trim()
    val buildYear: String = BuildConfig.BUILD_YEAR.trim()
    val versionName: String = BuildConfig.VERSION_NAME
    val versionCode: Int = BuildConfig.VERSION_CODE

    val hasDeveloperName: Boolean get() = developerName.isNotBlank()
    val hasContactEmail: Boolean get() = contactEmail.isNotBlank()
    val hasWebsiteUrl: Boolean get() = websiteUrl.isNotBlank()
    val hasPrivacyPolicyUrl: Boolean get() = privacyPolicyUrl.isNotBlank()
    val hasDownloadUrl: Boolean get() = downloadUrl.isNotBlank()
}
