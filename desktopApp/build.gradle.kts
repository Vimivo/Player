plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.detekt)
}

val macosMpvBridge by tasks.registering(Exec::class) {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    val javaHome = providers.environmentVariable("JAVA_HOME").orElse("/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home")
    val output = layout.buildDirectory.file("native/libvimivo_mpv_cocoa.dylib")
    inputs.file("src/desktopMain/native/macos/MpvCocoaRenderer.mm")
    outputs.file(output)
    commandLine(
        "xcrun", "clang++", "-dynamiclib", "-std=c++17",
        "-I${javaHome.get()}/include", "-I${javaHome.get()}/include/darwin", "-I/opt/homebrew/include",
        "src/desktopMain/native/macos/MpvCocoaRenderer.mm", "-o", output.get().asFile.absolutePath,
        "-L/opt/homebrew/lib", "-L${javaHome.get()}/lib", "-lmpv", "-ljawt", "-framework", "Cocoa", "-framework", "OpenGL", "-framework", "QuartzCore",
    )
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(macosMpvBridge)
}

tasks.matching { it.name == "run" }.configureEach {
    dependsOn(macosMpvBridge)
    (this as JavaExec).jvmArgs("-Djava.library.path=${layout.buildDirectory.dir("native").get().asFile.absolutePath}")
}

kotlin {
    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(libs.coroutines.core)
                implementation(libs.jna)
                implementation(libs.sqlite.jdbc)
                implementation(libs.jaudiotagger)
                implementation(libs.coil.compose)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.vimivo.desktop.MainKt"
        nativeDistributions {
            val operatingSystem = org.gradle.internal.os.OperatingSystem.current()
            val formats = when {
                operatingSystem.isWindows -> arrayOf(
                    org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                    org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                )
                operatingSystem.isMacOsX -> arrayOf(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
                operatingSystem.isLinux -> arrayOf(
                    org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                    org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm,
                    org.jetbrains.compose.desktop.application.dsl.TargetFormat.AppImage,
                )
                else -> emptyArray()
            }
            targetFormats(*formats)
            packageName = "Vimivo"
            packageVersion = "1.0.0"
            description = "Privacy-first music and video player"
            vendor = "Vimivo"
            macOS {
                bundleID = "dev.vimivo.desktop"
                minimumSystemVersion = "12.0"
            }
            windows {
                upgradeUuid = "e32d5e8d-901d-4d16-9b26-32d2fe47f890"
                menuGroup = "Vimivo"
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    source.setFrom("src/desktopMain/kotlin", "src/desktopTest/kotlin")
}

dependencies { detektPlugins(libs.detekt.formatting) }
