import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.rizzoplayer.iptv"
    compileSdk = 34
    flavorDimensions += "version"

    defaultConfig {
        applicationId = "com.rizzoplayer.iptv"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "TMDB_BEARER", "\"${localProps.getProperty("TMDB_BEARER", "")}\"")
        buildConfigField("String", "TORBOX_API_KEY", "\"${localProps.getProperty("TORBOX_API_KEY", "")}\"")
        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${localProps.getProperty("TRAKT_CLIENT_ID", "")}\"")
    }

    // v2 variant: same codebase, separate package so it coexists with v1 on the same device
    // v3 variant: same codebase, separate package for v3 preview/testing
    productFlavors {
        create("v2") {
            dimension = "version"
            applicationIdSuffix = ".v2"
            versionName = "2.0.0"
            versionCode = 2
        }
        create("v3") {
            dimension = "version"
            applicationIdSuffix = ".v3"
            versionName = "3.0.0"
            versionCode = 3
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = localProps.getProperty("RELEASE_KEYSTORE_PATH")
            val keystorePw = localProps.getProperty("RELEASE_KEYSTORE_PASSWORD")
            val keyAliasValue = localProps.getProperty("RELEASE_KEY_ALIAS")
            val keyPw = localProps.getProperty("RELEASE_KEY_PASSWORD")

            if (keystorePath != null) {
                require(File(keystorePath).exists()) {
                    "RELEASE_KEYSTORE_PATH is set to '$keystorePath' but file does not exist"
                }
                storeFile = File(keystorePath)
                storePassword = keystorePw ?: ""
                keyAlias = keyAliasValue ?: ""
                keyPassword = keyPw ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile != null && releaseConfig.storeFile!!.exists()) {
                signingConfig = releaseConfig
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = providers.gradleProperty("compose.compiler.version")
            .orElse("1.5.13").get()
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

tasks.configureEach {
    if (name.contains("lint", ignoreCase = true)) {
        enabled = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation(libs.androidx.core.ktx)

    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Unit tests
    testImplementation(libs.okhttp)
    testImplementation(libs.gson)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.11")

    // Instrumented tests
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("io.mockk:mockk-android:1.13.11")
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
