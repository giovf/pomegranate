// Root build file. Plugins are declared here (apply false) so every module
// resolves the same pinned versions from gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
