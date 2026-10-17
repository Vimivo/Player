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
    val commandLineToolsCompiler = file("/Library/Developer/CommandLineTools/usr/bin/clang++")
    val compiler = if (commandLineToolsCompiler.canExecute()) commandLineToolsCompiler.absolutePath else "clang++"
    val cmd = mutableListOf(
        compiler, "-dynamiclib", "-std=c++17",
        "-I${javaHome.get()}/include", "-I${javaHome.get()}/include/darwin", "-I/opt/homebrew/include",
        "src/desktopMain/native/macos/MpvCocoaRenderer.mm", "-o", output.get().asFile.absolutePath,
        "-L/opt/homebrew/lib", "-L${javaHome.get()}/lib", "-lmpv", "-ljawt", "-framework", "Cocoa", "-framework", "OpenGL", "-framework", "QuartzCore", "-framework", "MediaPlayer",
    )
    val stableSdk = file("/Library/Developer/CommandLineTools/SDKs/MacOSX26.sdk")
    if (stableSdk.exists()) {
        cmd.addAll(listOf("-isysroot", stableSdk.absolutePath))
    }
    commandLine(cmd)
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
            modules(
                "java.base",
                "java.desktop",
                "java.sql",
                "java.instrument",
                "java.logging",
                "java.prefs",
                "java.xml",
                "jdk.unsupported",
                "jdk.crypto.ec",
            )
            macOS {
                bundleID = "dev.vimivo.desktop"
                minimumSystemVersion = "12.0"
                iconFile.set(project.file("src/desktopMain/resources/macos/icon.icns"))
            }
            windows {
                upgradeUuid = "e32d5e8d-901d-4d16-9b26-32d2fe47f890"
                menuGroup = "Vimivo"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                iconFile.set(project.file("src/desktopMain/resources/windows/icon.ico"))
            }
            linux {
                iconFile.set(project.file("src/desktopMain/resources/linux/icon.png"))
                appCategory = "AudioVideo"
                menuGroup = "AudioVideo"
                rpmLicenseType = "Apache-2.0"
                debPackageVersion = "1.0.0"
                appRelease = "1"
                shortcut = true
            }
        }
    }
}

val bundleMacosMpv by tasks.registering(Exec::class) {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    dependsOn("createDistributable")
    dependsOn(macosMpvBridge)
    val appPath = layout.buildDirectory.dir("compose/binaries/main/app/Vimivo.app")
    val bridgeDylib = layout.buildDirectory.file("native/libvimivo_mpv_cocoa.dylib")
    commandLine(
        "python3",
        project.file("scripts/bundle_macos_mpv.py").absolutePath,
        appPath.get().asFile.absolutePath,
        bridgeDylib.get().asFile.absolutePath,
    )
}

val bundleWindowsMpv by tasks.registering(Exec::class) {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    dependsOn("createDistributable")
    val appPath = layout.buildDirectory.dir("compose/binaries/main/app/Vimivo")
    val nativeDir = providers.environmentVariable("VIMIVO_WINDOWS_MPV_DIR").orElse("")
    commandLine(
        "python",
        project.file("scripts/bundle_windows_mpv.py").absolutePath,
        appPath.get().asFile.absolutePath,
        nativeDir.get(),
    )
}

val bundleLinuxMpv by tasks.registering(Exec::class) {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    dependsOn("createDistributable")
    val appPath = layout.buildDirectory.dir("compose/binaries/main/app/Vimivo")
    val nativeDir = providers.environmentVariable("VIMIVO_LINUX_MPV_DIR").orElse("")
    commandLine(
        "python3",
        project.file("scripts/bundle_linux_mpv.py").absolutePath,
        appPath.get().asFile.absolutePath,
        nativeDir.get(),
    )
}

val packageAppImage by tasks.registering(Exec::class) {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isLinux }
    dependsOn("createDistributable")
    dependsOn(bundleLinuxMpv)
    val appPath = layout.buildDirectory.dir("compose/binaries/main/app/Vimivo")
    val outputDir = layout.buildDirectory.dir("compose/binaries/main/appimage")
    commandLine(
        project.file("scripts/build_appimage.sh").absolutePath,
        appPath.get().asFile.absolutePath,
        outputDir.get().asFile.absolutePath,
    )
}

tasks.matching { it.name == "createDistributable" }.configureEach {
    when {
        org.gradle.internal.os.OperatingSystem.current().isMacOsX -> finalizedBy(bundleMacosMpv)
        org.gradle.internal.os.OperatingSystem.current().isWindows -> finalizedBy(bundleWindowsMpv)
        org.gradle.internal.os.OperatingSystem.current().isLinux -> finalizedBy(bundleLinuxMpv)
    }
}

tasks.matching {
    val isMac = org.gradle.internal.os.OperatingSystem.current().isMacOsX
    isMac && it.name in listOf(
        "packageDmg",
        "packageDistributionForCurrentOS",
        "packageReleaseDmg",
        "packageReleaseDistributionForCurrentOS",
    )
}.configureEach {
    dependsOn(bundleMacosMpv)
}

tasks.matching {
    val isWin = org.gradle.internal.os.OperatingSystem.current().isWindows
    isWin && it.name in listOf(
        "packageExe",
        "packageMsi",
        "packageReleaseExe",
        "packageReleaseMsi",
        "packageDistributionForCurrentOS",
        "packageReleaseDistributionForCurrentOS",
    )
}.configureEach {
    dependsOn(bundleWindowsMpv)
}

tasks.matching {
    val isLinux = org.gradle.internal.os.OperatingSystem.current().isLinux
    isLinux && it.name in listOf(
        "packageDeb",
        "packageRpm",
        "packageReleaseDeb",
        "packageReleaseRpm",
        "packageDistributionForCurrentOS",
        "packageReleaseDistributionForCurrentOS",
    )
}.configureEach {
    dependsOn(bundleLinuxMpv)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    source.setFrom("src/desktopMain/kotlin", "src/desktopTest/kotlin")
}

dependencies { detektPlugins(libs.detekt.formatting) }
