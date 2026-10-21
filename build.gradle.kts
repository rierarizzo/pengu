plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

// One formatter/linter for every module, so CI can gate on it.
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        // Generated sources (Compose resources, etc.) are not ours to style. The
        // predicate form keeps the exclusion working on Windows, where the
        // Ant-style patterns do not match backslash paths.
        filter {
            exclude { element ->
                val path = element.file.path.replace('\\', '/')
                "/build/" in path || "/generated/" in path
            }
        }
    }
}