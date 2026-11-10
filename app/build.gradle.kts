import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.raajan.missedcallsos"
    minSdk = 26
    targetSdk = 36
    versionCode = 2
    versionName = "1.0.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val devName = (project.findProperty("developerName") as? String) ?: "RAAJAN"
    val devEmail = (project.findProperty("contactEmail") as? String) ?: "missedcallsos@gmail.com"
    val webUrl = (project.findProperty("appWebsiteUrl") as? String) ?: "https://github.com/RAJAN00005/MissedCallSOS"
    val privacyUrl = (project.findProperty("appPrivacyPolicyUrl") as? String) ?: ""
    val dlUrl = (project.findProperty("downloadUrl") as? String) ?: "https://github.com/RAJAN00005/MissedCallSOS/releases"
    val currentYear = "2026"

    buildConfigField("String", "DEVELOPER_NAME", "\"$devName\"")
    buildConfigField("String", "CONTACT_EMAIL", "\"$devEmail\"")
    buildConfigField("String", "WEBSITE_URL", "\"$webUrl\"")
    buildConfigField("String", "PRIVACY_POLICY_URL", "\"$privacyUrl\"")
    buildConfigField("String", "DOWNLOAD_URL", "\"$dlUrl\"")
    buildConfigField("String", "BUILD_YEAR", "\"$currentYear\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }

}


// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.libphonenumber)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

abstract class CheckNoSeededContactsTask : DefaultTask() {
  @get:InputDirectory
  abstract val mainDir: DirectoryProperty

  @TaskAction
  fun check() {
    val forbiddenPatterns = listOf(
      Regex("""\+?\b\d{10,13}\b"""),
      Regex("Emergency Helpline", RegexOption.IGNORE_CASE),
      Regex("""["'](Mom|Dad)["']""")
    )

    var violations = 0
    mainDir.get().asFile.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "xml" || it.extension == "json") }.forEach { file ->
      file.useLines { lines ->
        lines.forEachIndexed { index, line ->
          for (pattern in forbiddenPatterns) {
            if (pattern.containsMatchIn(line)) {
              println("ERROR: Forbidden seeded pattern found in ${file.path}:${index + 1} -> $line")
              violations++
            }
          }
        }
      }
    }

    if (violations > 0) {
      throw GradleException("checkNoSeededContacts failed: Found $violations forbidden seeded contact patterns in src/main!")
    } else {
      println("checkNoSeededContacts passed: src/main is completely clean of seeded contacts.")
    }
  }
}

val checkNoSeededContacts = tasks.register<CheckNoSeededContactsTask>("checkNoSeededContacts") {
  description = "Scans src/main to ensure no hardcoded seeded contacts or phone number literals exist"
  group = "verification"
  mainDir.set(layout.projectDirectory.dir("src/main"))
}

tasks.named("preBuild").configure {
  dependsOn(checkNoSeededContacts)
}


