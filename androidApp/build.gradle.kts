plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/**
 * Vimivo's LGPL media package (libmpv + FFmpeg, ADR-005): VIMIVO_ANDROID_MEDIA_DIR names an unpacked package (a local
 * scripts/build_media build); otherwise the package pinned in scripts/build_media/media.lock is downloaded from our
 * own GitHub Release and its SHA-256 verified before the build uses it.
 */
val androidMediaOverride: String? = providers.environmentVariable("VIMIVO_ANDROID_MEDIA_DIR").orNull
val androidMediaDir: File = androidMediaOverride?.let(::file)
    ?: layout.buildDirectory.dir("vimivo-media/android").get().asFile

val fetchAndroidMedia by tasks.registering(Exec::class) {
    val override = androidMediaOverride
    onlyIf { override == null }
    inputs.file(rootProject.file("scripts/build_media/media.lock"))
    outputs.dir(androidMediaDir)
    commandLine(
        "python3",
        rootProject.file("scripts/build_media/fetch_media.py").absolutePath,
        "--target", "android",
        "--dest", androidMediaDir.absolutePath,
    )
}

android {
    namespace = "dev.vimivo.app"
    compileSdk = 36
    ndkVersion = "27.1.12297006"

    defaultConfig {
        applicationId = providers.gradleProperty("vimivo.applicationId").orElse("dev.vimivo.app").get()
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("vimivo.versionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("vimivo.versionName").orElse("1.0.0").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild { cmake { arguments += "-DVIMIVO_ANDROID_MEDIA=${androidMediaDir.absolutePath}" } }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    sourceSets["main"].jniLibs.srcDir(androidMediaDir.resolve("jniLibs"))

    buildFeatures { compose = true }
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"

    val releaseStoreFile = providers.gradleProperty("vimivo.signing.storeFile").orNull
    val releaseStorePassword = providers.gradleProperty("vimivo.signing.storePassword").orNull
    val releaseKeyAlias = providers.gradleProperty("vimivo.signing.keyAlias").orNull
    val releaseKeyPassword = providers.gradleProperty("vimivo.signing.keyPassword").orNull
    val hasReleaseSigning = listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.navigation.compose)
    implementation(libs.coroutines.guava)
    implementation(libs.media3.common)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.session)
    implementation(libs.media3.ui.compose)
    implementation(libs.media3.ui)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.documentfile)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    detektPlugins(libs.detekt.formatting)
}

room { schemaDirectory("$projectDir/schemas") }

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
}

tasks.named("preBuild") { dependsOn(fetchAndroidMedia) }

