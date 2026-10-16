package dev.vimivo.desktop.architecture

import dev.vimivo.playback.PlayerEngine
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureBoundaryTest {

    private val projectRoot: File by lazy {
        var current = File(".").canonicalFile
        while (current.parentFile != null && !File(current, "settings.gradle.kts").exists()) {
            current = current.parentFile
        }
        current
    }

    @Test
    fun commonMainMustNotContainPlatformImports() {
        val commonMainDir = File(projectRoot, "shared/src/commonMain/kotlin")
        assertTrue(commonMainDir.exists(), "shared/src/commonMain/kotlin directory must exist")

        val forbiddenImportPrefixes = listOf(
            "import java.sql",
            "import java.io",
            "import java.nio",
            "import javax.",
            "import dev.vimivo.desktop",
            "import dev.vimivo.platform.android",
            "import dev.vimivo.platform.ios",
        )

        val violations = mutableListOf<String>()

        commonMainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    for (prefix in forbiddenImportPrefixes) {
                        if (trimmed.startsWith(prefix)) {
                            violations += "${file.relativeTo(projectRoot)}:${index + 1}: $trimmed"
                        }
                    }
                    // android.* check (allowing androidx.compose, androidx.navigation, and androidx.lifecycle)
                    if (trimmed.startsWith("import android.") && !trimmed.startsWith("import androidx.")) {
                        violations += "${file.relativeTo(projectRoot)}:${index + 1}: $trimmed"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found platform imports leaking into commonMain:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun androidAppMustNotDependOnDesktop() {
        val androidMainDir = File(projectRoot, "androidApp/src/main/kotlin")
        if (!androidMainDir.exists()) return

        val violations = mutableListOf<String>()
        androidMainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import dev.vimivo.desktop") || trimmed.startsWith("import com.apple")) {
                        violations += "${file.relativeTo(projectRoot)}:${index + 1}: $trimmed"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found desktop/apple imports leaking into androidApp:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun desktopAppMustNotDependOnAndroid() {
        val desktopMainDir = File(projectRoot, "desktopApp/src/desktopMain/kotlin")
        assertTrue(desktopMainDir.exists(), "desktopApp/src/desktopMain/kotlin directory must exist")

        val violations = mutableListOf<String>()
        desktopMainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import android.") || trimmed.startsWith("import androidx.room")) {
                        violations += "${file.relativeTo(projectRoot)}:${index + 1}: $trimmed"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found android/room imports leaking into desktopApp:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun coreDomainModelsMustNotDependOnUI() {
        val modelDir = File(projectRoot, "shared/src/commonMain/kotlin/dev/vimivo/core/model")
        assertTrue(modelDir.exists(), "core/model directory must exist")

        val violations = mutableListOf<String>()
        modelDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("import androidx.compose") || trimmed.startsWith("import dev.vimivo.ui")) {
                        violations += "${file.relativeTo(projectRoot)}:${index + 1}: $trimmed"
                    }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Found UI imports leaking into core domain models:\n" + violations.joinToString("\n"),
        )
    }

    @Test
    fun playerEngineContractIsPlatformNeutral() {
        val methods = PlayerEngine::class.java.methods
        for (method in methods) {
            val returnType = method.returnType.name
            assertTrue(
                !returnType.startsWith("android.") && !returnType.startsWith("java.sql.") &&
                    !returnType.startsWith("org.videolan."),
                "PlayerEngine method ${method.name} must not return platform-specific type: $returnType",
            )
            for (param in method.parameterTypes) {
                val paramType = param.name
                assertTrue(
                    !paramType.startsWith("android.") && !paramType.startsWith("java.sql.") &&
                        !paramType.startsWith("org.videolan."),
                    "PlayerEngine method ${method.name} must not accept platform-specific type: $paramType",
                )
            }
        }
    }
}
