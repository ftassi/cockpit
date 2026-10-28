plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

    buildTypes {
        // Media volume set by car mode: in the car the AUX input needs it
        // maxed out, at the desk that would be deafening.
        debug {
            buildConfigField("float", "CAR_VOLUME", "0.25f")
        }
        release {
            buildConfigField("float", "CAR_VOLUME", "1.0f")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
