import com.android.build.api.variant.impl.VariantOutputImpl

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("org.jetbrains.kotlin.plugin.serialization")
}

android {
  namespace = "ai.openclaw.tv"
  compileSdk = 36

  defaultConfig {
    applicationId = "ai.openclaw.tv"
    minSdk = 29  // Android TV 10+
    targetSdk = 36
    versionCode = 202601290
    versionName = "2026.1.29"
  }

  signingConfigs {
    create("release") {
      // For testing: use debug keystore if no release keystore configured
      storeFile = file(System.getenv("RELEASE_STORE_FILE") ?: "${System.getProperty("user.home")}/.android/debug.keystore")
      storePassword = System.getenv("RELEASE_STORE_PASSWORD") ?: "android"
      keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "androiddebugkey"
      keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      isDebuggable = true
    }
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }

  lint {
    // TV-specific lint checks we can ignore
    disable += setOf(
      "MissingLeanbackLauncher",
      "ImmersiveModeConfirmation",
      "IconLauncherShape"
    )
    warningsAsErrors = true
  }

  testOptions {
    unitTests.isIncludeAndroidResources = true
  }

  // Rename APK output
  androidComponents {
    onVariants { variant ->
      variant.outputs
        .filterIsInstance<VariantOutputImpl>()
        .forEach { output ->
          val versionName = output.versionName.orNull ?: "0"
          val buildType = variant.buildType
          val outputFileName = "openclaw-tv-${versionName}-${buildType}.apk"
          output.outputFileName = outputFileName
        }
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    allWarningsAsErrors.set(false)
  }
}

dependencies {
  val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Android Core
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
  implementation("androidx.activity:activity-compose:1.12.2")
  implementation("androidx.activity:activity-ktx:1.10.2")

  // Android TV / Leanback
  implementation("androidx.leanback:leanback:1.2.0")
  implementation("androidx.leanback:leanback-preference:1.2.0")

  // Compose
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")

  // Media & Video
  implementation("androidx.media3:media3-exoplayer:1.6.1")
  implementation("androidx.media3:media3-ui:1.6.1")
  implementation("androidx.media3:media3-common:1.6.1")

  // WebView
  implementation("androidx.webkit:webkit:1.15.0")

  // Material Components
  implementation("com.google.android.material:material:1.13.0")

  // Kotlin Coroutines & Serialization
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

  // Security & Crypto
  implementation("androidx.security:security-crypto:1.1.0")
  implementation("org.bouncycastle:bcprov-jdk18on:1.78")

  // Networking
  implementation("com.squareup.okhttp3:okhttp:5.3.2")

  // DNS-SD (Bonjour discovery)
  implementation("dnsjava:dnsjava:3.6.4")

  // Testing
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("io.kotest:kotest-runner-junit5-jvm:6.0.7")
  testImplementation("io.kotest:kotest-assertions-core-jvm:6.0.7")
  testImplementation("org.robolectric:robolectric:4.16")
  testRuntimeOnly("org.junit.vintage:junit-vintage-engine:6.0.2")

  debugImplementation("androidx.compose.ui:ui-tooling")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}
