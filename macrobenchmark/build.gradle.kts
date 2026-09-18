plugins {
    alias(libs.plugins.android.test)
}

android {
    namespace = "com.azimulkabir.actua.macrobenchmark"
    compileSdk {
        version = release(37)
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 37

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    // "benchmarkRelease" is generated automatically on :app by the androidx.baselineprofile
    // plugin: release-equivalent code (R8, no debuggable) but debug-signed and profileable, so
    // these tests can run locally/CI without release signing secrets while still measuring
    // production-shaped behavior rather than the debug build's unoptimized bytecode and JIT
    // warm-up (see docs/performance-baseline.md, and the acceptance criteria on #321).
    buildTypes {
        // A same-named "debug" build type on :app would win the match before any
        // matchingFallbacks are consulted, so this needs its own name to reach :app's
        // release-shaped "benchmarkRelease" variant instead.
        create("benchmark") {
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("benchmarkRelease")
        }
    }
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
}
