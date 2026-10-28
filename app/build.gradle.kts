import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// One stable signing key for every build, kept outside the repo (created by
// `make keystore`): Android refuses updates signed with a different key, and
// the Spotify SDK registration is tied to its SHA1.
val keystoreProperties = File(System.getProperty("user.home"), ".config/cockpit/keystore.properties")
if (!keystoreProperties.exists()) {
    throw GradleException("Missing $keystoreProperties: run `make keystore` first")
}

android {
    namespace = "dev.ftassi.cockpit"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.ftassi.cockpit"
        minSdk = 35
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        create("cockpit") {
            val props = Properties().apply { keystoreProperties.inputStream().use(::load) }
            storeFile = File(props.getProperty("storeFile"))
            storePassword = props.getProperty("storePassword")
            keyAlias = props.getProperty("keyAlias")
            keyPassword = props.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("cockpit")
        }
        release {
            signingConfig = signingConfigs.getByName("cockpit")
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.all {
            it.testLogging { events("passed", "skipped", "failed") }
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
