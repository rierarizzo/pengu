import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":app:shared"))
    implementation(project(":app:player"))
    implementation(project(":core"))
    implementation(project(":data"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.ktor.clientCio)

    // Image loading: the shared UI renders through Coil, and this is where the
    // desktop ImageLoader is configured (the default decoder aliases).
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)

    // Windows title-bar theming (DWM) goes through JNA.
    implementation(libs.jna)
    implementation(libs.jna.platform)
    implementation(libs.logback)

    // Desktop playback engine: mediamp's mpv backend renders the video into the
    // Compose canvas, so the Compose controls overlay it in the same window.
    implementation(libs.mediamp.all)
    runtimeOnly(libs.mediamp.mpv.runtime.windows.x64)
    runtimeOnly(libs.mediamp.ffmpeg.runtime.windows.x64)

    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.misgus.pengu.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.misgus.pengu"
            packageVersion = "1.0.0"

            windows {
                iconFile.set(rootProject.file("branding/pengu.ico"))
            }
        }
    }
}
