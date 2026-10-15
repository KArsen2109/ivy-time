plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.zham.app"
    compileSdk = libs.versions.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "com.zham.app"
        minSdk = libs.versions.min.sdk.get().toInt()
        targetSdk = libs.versions.compile.sdk.get().toInt()
        versionCode = 13
        versionName = "1.0.1"
    }

    // One fixed debug key, kept in the repo, so every build is signed alike and a new APK can be
    // installed over the last one without uninstalling it (which would delete the user's data).
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    // The key that signs what is uploaded to Google Play. It never lives in the repo: the build
    // finds it through these environment variables (the CI fills them from its secrets), and
    // without them the release build is simply left unsigned.
    val uploadKeystore = System.getenv("ZHAM_UPLOAD_KEYSTORE")?.takeIf { it.isNotBlank() }
    if (uploadKeystore != null) {
        signingConfigs {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = System.getenv("ZHAM_UPLOAD_STORE_PASSWORD")
                keyAlias = System.getenv("ZHAM_UPLOAD_KEY_ALIAS")
                keyPassword = System.getenv("ZHAM_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (uploadKeystore != null) signingConfig = signingConfigs.getByName("upload")
        }
        // The release build (shrunk and obfuscated) signed with the debug key, so it can be
        // installed over a debug build and tried on a phone before anything is uploaded.
        create("preview") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
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
    }
}

dependencies {
    implementation(projects.domain)
    implementation(projects.data)
    implementation(projects.design)

    implementation(libs.bundles.compose)
    implementation(libs.compose.activity)
    implementation(libs.compose.viewmodel)
    implementation(libs.compose.navigation)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.arrow.core)

    debugImplementation(libs.compose.tooling)

    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
}
