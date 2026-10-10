plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.saeed.core.litertlm"
    compileSdk = 35
    buildToolsVersion = "35.0.1"

    defaultConfig {
        // 24 = the minimum declared by litertlm-android itself.
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // `implementation` on purpose: LiteRT-LM's Kotlin metadata (2.4) must never reach a
    // consumer's Kotlin compile classpath, or a Kotlin < 2.4 project fails to build.
    implementation(libs.litertlm.android)

    testImplementation(libs.junit)
}