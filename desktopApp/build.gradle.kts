plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.detekt)
}

val isMacHost = org.gradle.internal.os.OperatingSystem.current().isMacOsX

/**
 * VIMIVO_MPV_PREFIX is the unpacked LGPL media package (scripts/build_media/fetch_media.py; lib/ + include/mpv).
 * Without it a local Homebrew prefix is used for development only (/opt/homebrew on Apple Silicon, /usr/local
 * on Intel); distributable builds always set it (ADR-004).
 */
val macosMpvPrefix: Provider<String> = providers.environmentVariable("VIMIVO_MPV_PREFIX")
    .orElse(providers.environmentVariable("HOMEBREW_PREFIX"))
    .orElse(if (System.getProperty("os.arch") == "aarch64") "/opt/homebrew" else "/usr/local")

val macosMpvBridge by tasks.registering(Exec::class) {
    // A local copy keeps the configuration cache from capturing the build script object.
    val macHost = isMacHost
    onlyIf { macHost }
    // JNI/JAWT headers come from the JDK running Gradle unless JAVA_HOME names another one.
    val javaHome = providers.environmentVariable("JAVA_HOME").orElse(System.getProperty("java.home"))
    val mpvPrefix = macosMpvPrefix.get()
    val output = layout.buildDirectory.file("native/libvimivo_mpv_cocoa.dylib")
    inputs.file("src/desktopMain/native/macos/MpvCocoaRenderer.mm")
    inputs.property("mpvPrefix", mpvPrefix)
    outputs.file(output)
    val commandLineToolsCompiler = file("/Library/Developer/CommandLineTools/usr/bin/clang++")
    val compiler = if (commandLineToolsCompiler.canExecute()) commandLineToolsCompiler.absolutePath else "clang++"
    val cmd = mutableListOf(
        // ARC is required: the bridge keeps Objective-C objects in static strong references.
        compiler, "-dynamiclib", "-std=c++17", "-fobjc-arc",
        "-I${javaHome.get()}/include", "-I${javaHome.get()}/include/darwin", "-I$mpvPrefix/include",
        "src/desktopMain/native/macos/MpvCocoaRenderer.mm", "-o", output.get().asFile.absolutePath,
        "-L$mpvPrefix/lib", "-L${javaHome.get()}/lib", "-lmpv", "-ljawt", "-framework", "Cocoa", "-framework", "OpenGL", "-framework", "QuartzCore", "-framework", "MediaPlayer",
    )
    // VIMIVO_MACOS_SDK pins an SDK; otherwise a locally installed stable Command Line Tools SDK is preferred, and
    // failing that the active SDK from xcrun. Without -isysroot the Command Line Tools compiler may find no SDK
    // at all ("'Cocoa/Cocoa.h' file not found" on the macos-15 runner image).
    val pinnedSdk = providers.environmentVariable("VIMIVO_MACOS_SDK").orNull?.let(::file)
        ?: file("/Library/Developer/CommandLineTools/SDKs/MacOSX26.sdk")
    val sdkPath = pinnedSdk.takeIf(File::exists)?.absolutePath
        ?: if (macHost) {
            providers.exec { commandLine("xcrun", "--sdk", "macosx", "--show-sdk-path") }
                .standardOutput.asText.get().trim().takeIf(String::isNotEmpty)
        } else {
            null
        }
    sdkPath?.let { cmd.addAll(listOf("-isysroot", it)) }
    commandLine(cmd)
}

/** Builds the Windows media controls (SMTC) bridge; needs MSVC, e.g. a Visual Studio developer prompt. */
val windowsMediaControlsBridge by tasks.registering(Exec::class) {
    val buildable = org.gradle.internal.os.OperatingSystem.current().isWindows &&
        providers.environmentVariable("VCINSTALLDIR").isPresent
    onlyIf { buildable }
    val javaHome = providers.environmentVariable("JAVA_HOME").orElse(System.getProperty("java.home")).get()
    val source = file("src/desktopMain/native/windows/VimivoMediaControls.cpp")
    val outputDir = layout.buildDirectory.dir("native").get().asFile
    inputs.file(source)
    outputs.file(File(outputDir, "vimivo_media_controls.dll"))
    doFirst { outputDir.mkdirs() }
    commandLine(
        "cl", "/nologo", "/std:c++20", "/EHsc", "/O2", "/LD", "/permissive-",
        "/I$javaHome\\include", "/I$javaHome\\include\\win32",
        source.absolutePath, "windowsapp.lib",
        "/Fe:${File(outputDir, "vimivo_media_controls.dll").absolutePath}",
        "/Fo:${outputDir.absolutePath}\\",
    )
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(macosMpvBridge)
}

tasks.matching { it.name == "run" }.configureEach {
    dependsOn(macosMpvBridge)
    // The Compose plugin replaces the run task's jvmArgs, so the bridge path must be a system property.
    (this as JavaExec).systemProperty("java.library.path", layout.buildDirectory.dir("native").get().asFile.absolutePath)
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
                // Makes Dispatchers.Main the Swing event thread, where Compose Desktop and AWT already run.
                implementation(libs.coroutines.swing)
                implementation(libs.jna)
                // Win32 registry access for per-user file associations (same project and license as JNA).
                implementation(libs.jna.platform)
                implementation(libs.sqlite.jdbc)
                implementation(libs.jaudiotagger)
                implementation(libs.coil.compose)
                implementation(compose.components.resources)
                // koin-core only: koin-compose 4.2.2 drags Compose up to 1.10, mismatching Skiko's 1.9.3 natives.
                implementation(libs.koin.core)
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.koin.test)
                implementation(libs.coroutines.test)
            }
        }
    }
}

/** One row of `media-types.tsv`, the single source of file types for associations and the app itself. */
data class MediaTypeRow(val extension: String, val mime: String, val family: String, val description: String, val uti: String?)

val mediaTypes: List<MediaTypeRow> = file("src/desktopMain/resources/dev/vimivo/desktop/media-types.tsv")
    .readLines()
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .map { line ->
        val columns = line.split('\t')
        MediaTypeRow(columns[0], columns[1], columns[2], columns[3], columns.getOrNull(4)?.takeIf(String::isNotBlank))
    }

/**
 * macOS document types keyed by UTI, plus imported declarations for types macOS does not know (mkv, opus…).
 * LSHandlerRank "Alternate" lists Vimivo in "Open With" without claiming to be the default.
 */
fun macDocumentTypesXml(types: List<MediaTypeRow>): String {
    fun importedUti(type: MediaTypeRow) = "dev.vimivo.media.${type.extension}"
    fun uti(type: MediaTypeRow) = type.uti ?: importedUti(type)
    val conformsTo = mapOf(
        "video" to listOf("public.movie"),
        "audio" to listOf("public.audio"),
        "playlist" to listOf("public.data", "public.content"),
        "subtitle" to listOf("public.text"),
    )
    val broad = mapOf("video" to listOf("public.movie"), "audio" to listOf("public.audio"))
    val documentTypes = types.groupBy(MediaTypeRow::family).entries.joinToString("\n") { (family, rows) ->
        val utis = (broad[family].orEmpty() + rows.map(::uti)).distinct()
        """
        <dict>
            <key>CFBundleTypeName</key><string>${family.replaceFirstChar(Char::uppercase)}</string>
            <key>CFBundleTypeRole</key><string>Viewer</string>
            <key>LSHandlerRank</key><string>Alternate</string>
            <key>LSItemContentTypes</key><array>${utis.joinToString("") { "<string>$it</string>" }}</array>
        </dict>
        """.trimIndent()
    }
    val imported = types.filter { it.uti == null }.joinToString("\n") { type ->
        """
        <dict>
            <key>UTTypeIdentifier</key><string>${importedUti(type)}</string>
            <key>UTTypeDescription</key><string>${type.description}</string>
            <key>UTTypeConformsTo</key><array>${conformsTo.getValue(type.family).joinToString("") { "<string>$it</string>" }}</array>
            <key>UTTypeTagSpecification</key><dict>
                <key>public.filename-extension</key><array><string>${type.extension}</string></array>
                <key>public.mime-type</key><array><string>${type.mime}</string></array>
            </dict>
        </dict>
        """.trimIndent()
    }
    return """
        <key>CFBundleDocumentTypes</key>
        <array>
        $documentTypes
        </array>
        <key>UTImportedTypeDeclarations</key>
        <array>
        $imported
        </array>
    """.trimIndent()
}

compose.resources {
    packageOfResClass = "dev.vimivo.desktop.resources"
    generateResClass = always
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
            // "Open With" registration comes from the same table the app uses to classify files. Windows and
            // Linux use Compose's per-extension associations; macOS gets UTI-based document types below.
            mediaTypes.forEach { type ->
                windows.fileAssociation(type.mime, type.extension, type.description)
                linux.fileAssociation(type.mime, type.extension, type.description)
            }
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
                infoPlist { extraKeysRawXml = macDocumentTypesXml(mediaTypes) }
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

/**
 * Generates the JDK's base class-data-sharing archive inside the bundled runtime (`-Xshare:dump` through the
 * app's own launcher, which exits right after dumping). jlink drops this archive; restoring it shortens JVM
 * startup and is location-independent, unlike JDK 17 application archives. Runs before macOS signing.
 */
val dumpBaseCdsArchive by tasks.registering(Exec::class) {
    dependsOn("createDistributable")
    val os = org.gradle.internal.os.OperatingSystem.current()
    val appRoot = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
    val launcher = when {
        os.isMacOsX -> File(appRoot, "Vimivo.app/Contents/MacOS/Vimivo")
        os.isWindows -> File(appRoot, "Vimivo/Vimivo.exe")
        else -> File(appRoot, "Vimivo/bin/Vimivo")
    }
    environment("JAVA_TOOL_OPTIONS", "-Xshare:dump")
    commandLine(launcher.absolutePath)
}

val bundleMacosMpv by tasks.registering(Exec::class) {
    dependsOn(dumpBaseCdsArchive)
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
    dependsOn("createDistributable")
    dependsOn(macosMpvBridge)
    val appPath = layout.buildDirectory.dir("compose/binaries/main/app/Vimivo.app")
    val bridgeDylib = layout.buildDirectory.file("native/libvimivo_mpv_cocoa.dylib")
    environment("VIMIVO_MPV_PREFIX", macosMpvPrefix.get())
    commandLine(
        "python3",
        project.file("scripts/bundle_macos_mpv.py").absolutePath,
        appPath.get().asFile.absolutePath,
        bridgeDylib.get().asFile.absolutePath,
        project.file("macos/Vimivo.entitlements").absolutePath,
    )
}

val bundleWindowsMpv by tasks.registering(Exec::class) {
    dependsOn(dumpBaseCdsArchive)
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

val bundleWindowsMediaControls by tasks.registering(Copy::class) {
    onlyIf { org.gradle.internal.os.OperatingSystem.current().isWindows }
    dependsOn(windowsMediaControlsBridge)
    mustRunAfter("createDistributable")
    from(layout.buildDirectory.file("native/vimivo_media_controls.dll"))
    into(layout.buildDirectory.dir("compose/binaries/main/app/Vimivo"))
}

val bundleLinuxMpv by tasks.registering(Exec::class) {
    dependsOn(dumpBaseCdsArchive)
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

// Not "packageAppImage": Compose registers that name itself for TargetFormat.AppImage on Linux hosts,
// and a duplicate breaks configuration of the whole project there.
val packageVimivoAppImage by tasks.registering(Exec::class) {
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
        org.gradle.internal.os.OperatingSystem.current().isWindows -> finalizedBy(bundleWindowsMpv, bundleWindowsMediaControls)
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
    dependsOn(bundleWindowsMpv, bundleWindowsMediaControls)
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

// CI points the live libmpv tests at the pinned LGPL media package (scripts/build_media): VIMIVO_TEST_MPV_DIR is its
// library directory (bin/ on Windows, lib/ elsewhere). Without it the tests look for a local libmpv or skip.
tasks.withType<Test>().configureEach {
    providers.environmentVariable("VIMIVO_TEST_MPV_DIR").orNull?.let { dir ->
        val os = org.gradle.internal.os.OperatingSystem.current()
        val library = when {
            os.isWindows -> "libmpv-2.dll"
            os.isMacOsX -> "libmpv.2.dylib"
            else -> "libmpv.so.2"
        }
        systemProperty("vimivo.mpv.library", file(dir).resolve(library).absolutePath)
    }
    // A failing live test should say why in the CI log, not only that it failed.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
