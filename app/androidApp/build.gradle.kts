import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":app:shared"))
    implementation(project(":app:player"))
    implementation(project(":data"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.profileinstaller)

    // Android playback engine: mediamp resolves its all-in-one bundle to the
    // ExoPlayer (media3) backend on Android and embeds a `PlayerView` through
    // `AndroidView`, so the Compose controls stack on top of the video.
    implementation(libs.mediamp.all)

    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.misgus.pengu"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.misgus.pengu"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            // Minify and shrink the release build: the app ships unoptimised
            // otherwise, and it is the single biggest Android perf lever. The
            // library baseline profiles (Compose and friends) are merged into
            // the APK, and `profileinstaller` installs them.
            isMinifyEnabled = true
            isShrinkResources = true
            // Local testing: sign the release with the debug key so the IDE can
            // install and run this variant. Replace it with a real keystore
            // before shipping a distributable build.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
