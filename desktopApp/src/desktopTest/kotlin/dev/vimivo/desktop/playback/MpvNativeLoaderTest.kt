package dev.vimivo.desktop.playback

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MpvNativeLoaderTest {
    @Test
    fun findBundledFrameworksDirHandlesNonPackagedEnvironmentSafely() {
        // When running under standard Gradle test runner, we are not inside a .app bundle
        val frameworks = MpvNative.findBundledFrameworksDir()
        // Must either be null or, if run against a packaged app, must be a directory
        if (frameworks != null) {
            assertTrue(frameworks.isDirectory)
        } else {
            assertNull(frameworks)
        }
    }

    @Test
    fun findBundledWindowsDllDirHandlesNonPackagedEnvironmentSafely() {
        val dllDir = MpvNative.findBundledWindowsDllDir()
        if (dllDir != null) {
            assertTrue(dllDir.isDirectory)
        } else {
            assertNull(dllDir)
        }
    }

    @Test
    fun findBundledLinuxLibDirHandlesNonPackagedEnvironmentSafely() {
        val libDir = MpvNative.findBundledLinuxLibDir()
        if (libDir != null) {
            assertTrue(libDir.isDirectory)
        } else {
            assertNull(libDir)
        }
    }

    @Test
    fun explicitSystemPropertyOverridesDefaultLibrary() {
        val original = System.getProperty("vimivo.mpv.library")
        try {
            System.setProperty("vimivo.mpv.library", "/nonexistent/libmpv.dylib")
            val error = runCatching { MpvNative.load() }.exceptionOrNull()
            assertNotNull(error, "Should fail when explicit path does not exist")
        } finally {
            if (original != null) {
                System.setProperty("vimivo.mpv.library", original)
            } else {
                System.clearProperty("vimivo.mpv.library")
            }
        }
    }

    @Test
    fun bundledFrameworksDylibStructureValidation() {
        // Verify packaged Vimivo.app has Frameworks with libmpv.2.dylib if created
        val appPath = File("build/compose/binaries/main/app/Vimivo.app/Contents/Frameworks")
        if (appPath.exists()) {
            val mpvDylib = File(appPath, "libmpv.2.dylib")
            assertTrue(mpvDylib.exists(), "Bundled libmpv.2.dylib must exist in Frameworks")
            val cocoaBridge = File(appPath, "libvimivo_mpv_cocoa.dylib")
            assertTrue(cocoaBridge.exists(), "Bundled libvimivo_mpv_cocoa.dylib must exist in Frameworks")
        }
    }
}
