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
    // mediamp-api (even 0.5.3) asks for compose.foundation 1.12.0 while the rest of
    // the Compose stack comes from the catalog at 1.12.1, and Compose's own
    // compatibility check refuses a mixed graph. foundation has no separate version
    // axis — it ships with the Compose Multiplatform release — so align it here once
    // instead of repeating a constraint in every module that pulls mediamp.
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.compose.foundation") {
                useVersion(libs.versions.composeMultiplatform.get())
            }
        }
    }

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