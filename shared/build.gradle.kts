plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.detekt)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/commonTest/kotlin")
}

dependencies { detektPlugins(libs.detekt.formatting) }

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        // The system SQLite for the shared library store; the Kotlin/Native platform libraries do not include it.
        iosTarget.compilations.getByName("main").cinterops.create("sqlite3") {
            defFile(project.file("src/nativeInterop/cinterop/sqlite3.def"))
        }
        iosTarget.binaries.framework {
            baseName = "VimivoShared"
            isStatic = true
            binaryOption("bundleId", "dev.vimivo.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
            implementation(libs.serialization.core)
            api(libs.serialization.json)
            implementation(libs.navigation.compose)
            implementation(compose.runtime)
            implementation(compose.foundation)
            api(compose.material3)
            api(compose.materialIconsExtended)
            api(libs.haze)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
        }
        // The library store is tested over JDBC SQLite on the JVM; iOS tests run it over the real iOS driver.
        val desktopTest by getting {
            dependencies { implementation(libs.sqlite.jdbc) }
        }
    }
}

android {
    namespace = "dev.vimivo.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

