import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "dev.orsetto.shaketime"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.orsetto.shaketime"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            // Populated from environment variables in CI when release-signing
            // secrets are configured. Left empty otherwise (see buildTypes).
            val storeFilePath = System.getenv("KEYSTORE_FILE")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Use the real release keystore when provided, otherwise fall back to
            // the debug keystore so `assembleRelease` still yields an installable
            // APK without any secrets configured.
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// ---------------------------------------------------------------------------
// The Glyph Matrix SDK is Nothing's closed-source library. Its licence forbids
// redistribution, so the .aar is NOT committed to this repository. Instead it
// is fetched from Nothing's official developer-kit repository at build time.
// You can also run scripts/fetch-glyph-sdk.sh or drop the file in app/libs/
// manually. See README.md.
// ---------------------------------------------------------------------------
val glyphSdkAar = layout.projectDirectory.file("libs/glyph-matrix-sdk-2.0.aar")

val downloadGlyphSdk by tasks.registering {
    description = "Downloads the Nothing Glyph Matrix SDK AAR from the official repository."
    group = "build setup"
    val out = glyphSdkAar.asFile
    outputs.file(out)
    onlyIf { !out.exists() }
    doLast {
        out.parentFile.mkdirs()
        val url =
            "https://raw.githubusercontent.com/Nothing-Developer-Programme/" +
                "GlyphMatrix-Developer-Kit/main/glyph-matrix-sdk-2.0.aar"
        logger.lifecycle("Downloading Glyph Matrix SDK from $url")
        try {
            URI(url).toURL().openStream().use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            out.delete()
            throw GradleException(
                "Could not download the Glyph Matrix SDK.\n" +
                    "Download it manually from " +
                    "https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit\n" +
                    "and place 'glyph-matrix-sdk-2.0.aar' into app/libs/.\n" +
                    "Cause: ${e.message}",
            )
        }
    }
}

tasks.named("preBuild") { dependsOn(downloadGlyphSdk) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(files(glyphSdkAar.asFile))
}
