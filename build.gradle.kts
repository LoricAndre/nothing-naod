plugins {
    // Kotlin is provided by AGP 9's built-in Kotlin support, so the standalone
    // Kotlin Gradle plugin is intentionally not declared here.
    alias(libs.plugins.android.application) apply false
}
