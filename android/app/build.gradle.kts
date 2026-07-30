plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.qezawat.iprocker"
    compileSdk = 35

    // Version metadata is injected by CI from the git tag so a release APK is
    // always traceable to a commit. Local builds fall back to 1.0.0.
    val appVersionName: String = System.getenv("ANDROID_VERSION_NAME")
        ?.takeIf { it.isNotBlank() } ?: "1.0.0"
    val appVersionCode: Int = System.getenv("ANDROID_VERSION_CODE")
        ?.toIntOrNull() ?: 1

    defaultConfig {
        applicationId = "com.qezawat.iprocker"
        minSdk = 24
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        resourceConfigurations += listOf("en")
    }

    signingConfigs {
        create("release") {
            // The keystore is written by CI from a secret. When it is absent
            // (local builds) the release type falls back to the debug key below.
            val ks = file("../keystore/release.keystore")
            if (ks.exists()) {
                storeFile = ks
                storePassword = System.getenv("ORG_GRADLE_PROJECT_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("ORG_GRADLE_PROJECT_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("ORG_GRADLE_PROJECT_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (file("../keystore/release.keystore").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
    }

    // Per-ABI APKs keep the download small; a universal APK is also produced
    // for users who do not know their device architecture.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            )
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // The Go scanner core, produced by gomobile into app/libs/iprocker.aar.
    implementation(files("libs/iprocker.aar"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.datastore.preferences)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
