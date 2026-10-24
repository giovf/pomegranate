import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Per-developer build configuration (gitignored). See .env.example for the keys.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val oauthClientId: String = localProperties.getProperty("pomegranate.oauthClientId", "")

// Release signing is optional: without these four properties the release
// build stays unsigned (fine for CI and for checking that R8 works).
val signingStoreFile: String? = localProperties.getProperty("pomegranate.signing.storeFile")
val signingStorePassword: String? = localProperties.getProperty("pomegranate.signing.storePassword")
val signingKeyAlias: String? = localProperties.getProperty("pomegranate.signing.keyAlias")
val signingKeyPassword: String? = localProperties.getProperty("pomegranate.signing.keyPassword")
val hasReleaseSigning = listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.gankdat.pomegranate"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.gankdat.pomegranate"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        // Public OAuth client id (no secret exists for Android clients); empty
        // until the developer adds pomegranate.oauthClientId to local.properties.
        buildConfigField("String", "OAUTH_CLIENT_ID", "\"$oauthClientId\"")
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(checkNotNull(signingStoreFile))
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        // "Newer version available" depends on what Google published today, so
        // with warnings-as-errors an upstream release would break an unchanged
        // checkout. Versions are bumped deliberately in libs.versions.toml and
        // pinned by checksum; check for updates with this lint id manually.
        disable += "GradleDependency"
    }

    testOptions {
        // Room's Android builder only needs a Context for low-RAM detection;
        // stubbed android.jar calls returning defaults let DAO tests run on
        // the host JVM against the bundled SQLite driver (no Robolectric).
        unitTests.isReturnDefaultValues = true
    }
}

room {
    // Exported schemas are committed so future migrations can be validated.
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.work.runtime)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.sqlite.bundled.jvm)
}
