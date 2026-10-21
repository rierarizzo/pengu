import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    jvm()

    android {
        namespace = "com.misgus.pengu.data"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
    }

    sourceSets {
        // Shared by the JVM and Android targets: both back the platform layer
        // with `java.io.File`, so the file helpers live here once.
        val jvmSharedMain =
            create("jvmSharedMain") {
                dependsOn(commonMain.get())
            }
        jvmMain.get().dependsOn(jvmSharedMain)
        androidMain.get().dependsOn(jvmSharedMain)

        jvmSharedMain.dependencies {
            // The shared DNS resolver speaks OkHttp's `Dns`, and both platform
            // addon clients run on OkHttp.
            implementation(libs.ktor.clientOkhttp)
        }
        commonMain.dependencies {
            api(project(":core"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            api(libs.ktor.clientCore)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.clientCio)
            implementation(libs.ktor.clientOkhttp)
        }
        androidMain.dependencies {
            implementation(libs.ktor.clientOkhttp)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.clientMock)
        }
    }
}
