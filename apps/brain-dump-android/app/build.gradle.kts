import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The release signing key lives outside the repo (see README). Locally it's read from
// D:/Metamorphosis/keys; CI points BRAIN_DUMP_KEYSTORE_PROPERTIES at a file it decodes from secrets.
val keystoreProps: Properties? = (System.getenv("BRAIN_DUMP_KEYSTORE_PROPERTIES") ?: "D:/Metamorphosis/keys/keystore.properties")
    .let(::File) // plain File: Gradle's file() treats "D:/..." as a URL on Linux CI
    .takeIf { it.exists() }
    ?.let { f -> Properties().apply { f.inputStream().use(::load) } }

android {
    namespace = "io.github.nithv.braindump"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.nithv.braindump"
        minSdk = 26
        targetSdk = 35
        // versionCode must only ever go up: Android refuses to install a lower one over a higher one.
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = File(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        debug {
            // Debug builds install side by side with the real app, so testing never touches your data.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        buildConfig = true
    }
    lint {
        warningsAsErrors = false
        abortOnError = true
        // Library upgrades are done on purpose, as their own reviewed change, not as lint noise.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
