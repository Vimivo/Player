@file:Suppress("FunctionNaming")

package dev.vimivo.desktop.playback

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File

internal interface MpvNative : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(context: Pointer): Int
    fun mpv_terminate_destroy(context: Pointer)
    fun mpv_set_option_string(context: Pointer, name: String, value: String): Int
    fun mpv_set_property_string(context: Pointer, name: String, value: String): Int
    fun mpv_get_property(context: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_get_property_string(context: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_command(context: Pointer, arguments: Pointer): Int
    fun mpv_error_string(code: Int): String?

    companion object {
        /** The macOS JAWT/Core Animation and Now Playing bridge (links libmpv); loading it again is a no-op. */
        fun loadCocoaBridge() {
            val bridge = findBundledFrameworksDir()?.resolve("libvimivo_mpv_cocoa.dylib")
            if (bridge?.exists() == true) System.load(bridge.absolutePath) else System.loadLibrary("vimivo_mpv_cocoa")
        }

        fun findBundledFrameworksDir(): File? {
            val codeSource = MpvNative::class.java.protectionDomain?.codeSource?.location ?: return null
            val contentsDir = runCatching { File(codeSource.toURI()) }.getOrNull()?.parentFile?.parentFile
            return contentsDir?.resolve("Frameworks")?.takeIf { it.exists() && it.isDirectory }
        }

        fun findBundledWindowsDllDir(): File? {
            val codeSource = MpvNative::class.java.protectionDomain?.codeSource?.location ?: return null
            val jarOrClassesDir = runCatching { File(codeSource.toURI()) }.getOrNull() ?: return null
            val appDir = if (jarOrClassesDir.isFile) jarOrClassesDir.parentFile else jarOrClassesDir
            val installDir = appDir?.parentFile
            val candidates = listOfNotNull(
                appDir,
                appDir?.resolve("bin"),
                installDir,
                installDir?.resolve("bin"),
                installDir?.resolve("runtime")?.resolve("bin"),
            )
            val dllNames = listOf("mpv-2.dll", "libmpv-2.dll", "libmpv.dll", "mpv.dll")
            return candidates.firstOrNull { dir ->
                dir.exists() && dir.isDirectory && dllNames.any { dll -> dir.resolve(dll).exists() }
            }
        }

        fun findBundledLinuxLibDir(): File? {
            val codeSource = MpvNative::class.java.protectionDomain?.codeSource?.location ?: return null
            val jarOrClassesDir = runCatching { File(codeSource.toURI()) }.getOrNull() ?: return null
            val appDir = if (jarOrClassesDir.isFile) jarOrClassesDir.parentFile else jarOrClassesDir
            val installDir = appDir?.parentFile
            val candidates = listOfNotNull(
                appDir,
                appDir?.resolve("lib"),
                installDir,
                installDir?.resolve("lib"),
                installDir?.resolve("runtime")?.resolve("lib"),
            )
            val soNames = listOf("libmpv.so.2", "libmpv.so.1", "libmpv.so")
            return candidates.firstOrNull { dir ->
                dir.exists() && dir.isDirectory && soNames.any { so -> dir.resolve(so).exists() }
            }
        }

        @Suppress("ReturnCount")
        fun load(): MpvNative {
            val explicit = System.getProperty("vimivo.mpv.library")?.takeIf(String::isNotBlank)
            if (explicit != null) {
                return Native.load(explicit, MpvNative::class.java)
            }
            val frameworks = findBundledFrameworksDir()
            if (frameworks != null) {
                val candidate = listOf("libmpv.2.dylib", "libmpv.dylib")
                    .map { File(frameworks, it) }
                    .firstOrNull(File::exists)
                if (candidate != null) {
                    appendJnaLibraryPath(frameworks)
                    return Native.load(candidate.absolutePath, MpvNative::class.java)
                }
            }
            val windowsDllDir = findBundledWindowsDllDir()
            if (windowsDllDir != null) {
                val candidate = listOf("mpv-2.dll", "libmpv-2.dll", "libmpv.dll", "mpv.dll")
                    .map { File(windowsDllDir, it) }
                    .firstOrNull(File::exists)
                if (candidate != null) {
                    appendJnaLibraryPath(windowsDllDir)
                    return Native.load(candidate.absolutePath, MpvNative::class.java)
                }
            }
            val linuxLibDir = findBundledLinuxLibDir()
            if (linuxLibDir != null) {
                val candidate = listOf("libmpv.so.2", "libmpv.so.1", "libmpv.so")
                    .map { File(linuxLibDir, it) }
                    .firstOrNull(File::exists)
                if (candidate != null) {
                    appendJnaLibraryPath(linuxLibDir)
                    return Native.load(candidate.absolutePath, MpvNative::class.java)
                }
            }
            val systemCandidates = listOf(File("/opt/homebrew/lib"), File("/usr/local/lib"))
            for (dir in systemCandidates) {
                if (dir.exists()) {
                    val candidate = listOf("libmpv.2.dylib", "libmpv.dylib")
                        .map { File(dir, it) }
                        .firstOrNull(File::exists)
                    if (candidate != null) {
                        appendJnaLibraryPath(dir)
                        return Native.load(candidate.absolutePath, MpvNative::class.java)
                    }
                }
            }
            // Distributions ship only the versioned soname unless the -dev package is installed.
            if (System.getProperty("os.name").orEmpty().startsWith("Linux")) {
                for (soname in listOf("libmpv.so.2", "libmpv.so.1")) {
                    runCatching { return Native.load(soname, MpvNative::class.java) }
                }
            }
            return Native.load("mpv", MpvNative::class.java)
        }

        private fun appendJnaLibraryPath(dir: File) {
            val currentJna = System.getProperty("jna.library.path")
            if (currentJna.isNullOrBlank()) {
                System.setProperty("jna.library.path", dir.absolutePath)
            } else if (!currentJna.contains(dir.absolutePath)) {
                System.setProperty("jna.library.path", "$currentJna${File.pathSeparator}${dir.absolutePath}")
            }
        }
    }
}
