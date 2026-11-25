plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

composeCompiler {
    val composeMetricsDir = layout.buildDirectory.dir("compose_metrics")
    metricsDestination = composeMetricsDir
    reportsDestination = composeMetricsDir
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
        versionCode = 86
        versionName = "2.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One distribution: the Google Play build, also published on GitHub as Actua-v<version>.apk.
    flavorDimensions += "distribution"
    productFlavors {
        create("playstore") {
            dimension = "distribution"
        }
    }

    signingConfigs {
        create("release") {
            releaseKeystorePath?.let { storeFile = file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    // Keep dependency metadata out of release signing blocks.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
                enable = true
                keepRules {
                    files.add(file("proguard-rules.pro"))
                }
            }
        }
    }
    testBuildType = "instrumented"
    // AGP built-in Kotlin inherits this target, keeping Java and Kotlin bytecode aligned.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Ship only the locales Actua translates (see docs/LOCALIZATION.md); dependency-library
    // locales for other languages would otherwise appear in the system picker without app strings.
    androidResources {
        localeFilters += listOf("en", "pt-rBR", "es", "fr", "de", "it", "ja", "ru", "zh-rCN")
    }
    bundle {
        language {
            enableSplit = false
        }
    }
}

// The in-app "What's new" card shows the release's Google Play text, so the build copies each language's
// fastlane/metadata/android/<play-language>/changelogs/<versionCode>.txt into the app's assets as
// whatsnew/<app-language>.txt (see WhatsNewNotes). English is required; a missing translation falls back to it.
abstract class BundleWhatsNewTask : DefaultTask() {
    @get:Input abstract val versionCode: Property<Int>
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val metadataDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun bundle() {
        val out = outputDir.get().asFile.resolve("whatsnew")
        out.deleteRecursively()
        out.mkdirs()
        val languages = mapOf(
            "en-US" to "en", "pt-BR" to "pt-BR", "es-ES" to "es", "fr-FR" to "fr", "de-DE" to "de",
            "it-IT" to "it", "ja-JP" to "ja", "ru-RU" to "ru", "zh-CN" to "zh-CN",
        )
        languages.forEach { (playLanguage, appLanguage) ->
            val notes = metadataDir.get().asFile.resolve("$playLanguage/changelogs/${versionCode.get()}.txt")
            if (notes.isFile) {
                notes.copyTo(out.resolve("$appLanguage.txt"), overwrite = true)
            } else if (playLanguage == "en-US") {
                throw GradleException("Missing $notes: add this release's \"What's new\" text (see .github/MAINTAINER_NOTES.md)")
            }
        }
    }
}

androidComponents {
    onVariants { variant ->
        val bundleWhatsNew = tasks.register<BundleWhatsNewTask>("bundleWhatsNew${variant.name.replaceFirstChar { it.uppercase() }}") {
            versionCode.set(android.defaultConfig.versionCode)
            metadataDir.set(rootProject.layout.projectDirectory.dir("fastlane/metadata/android"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(bundleWhatsNew, BundleWhatsNewTask::outputDir)
    }
}

// The benchmark build is installed next to the real app, never over it: same application ID and signing
// would either fail to install or need an uninstall that deletes the real app's data. :macrobenchmark
// targets this ID (see its DemoBudgetJourneys.kt).
androidComponents {
    onVariants(selector().withBuildType("benchmarkRelease")) { variant ->
        variant.applicationId.set("com.azimulkabir.actua.benchmark")
    }
}

baselineProfile {
    // Skip regenerating the profile on every build; run `:app:generateBaselineProfile`
    // manually (on a connected device/emulator) when app flows change meaningfully.
    automaticGenerationDuringBuild = false
}

dependencies {
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.profileinstaller)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.pdfbox.android)
    // WebDAV backups need PROPFIND, MKCOL and MOVE, which HttpURLConnection rejects.
    implementation(libs.okhttp)
    add("playstoreImplementation", libs.android.billing.ktx)
    // Google's in-app review sheet for the one-time rating request; playstore only.
    add("playstoreImplementation", libs.play.review.ktx)
    // On-device receipt text recognition (bundled models for Latin, Chinese, Japanese, Korean and Devanagari
    // scripts; nothing is sent anywhere); playstore only.
    add("playstoreImplementation", libs.mlkit.text.recognition)
    add("playstoreImplementation", libs.mlkit.text.recognition.chinese)
    add("playstoreImplementation", libs.mlkit.text.recognition.devanagari)
    add("playstoreImplementation", libs.mlkit.text.recognition.japanese)
    add("playstoreImplementation", libs.mlkit.text.recognition.korean)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    // Real org.json impl for JVM unit tests — the Android stub jar throws on use.
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.work.testing)
    add("instrumentedImplementation", libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
