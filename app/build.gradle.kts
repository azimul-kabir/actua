plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val releaseKeystorePath = providers.environmentVariable("ACTUA_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("ACTUA_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ACTUA_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ACTUA_KEY_PASSWORD").orNull

android {
    namespace = "com.azimulkabir.actua"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.azimulkabir.actua"
        minSdk = 28
        targetSdk = 37
        versionCode = 24
        versionName = "1.0.0-beta.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            releaseKeystorePath?.let { storeFile = file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        debug { }
        create("instrumented") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".test"
            matchingFallbacks += listOf("debug")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            optimization {
                enable = false
            }
        }
    }
    testBuildType = "instrumented"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    add("instrumentedImplementation", libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
