@file:Suppress("FunctionNaming")

package dev.vimivo.desktop.playback

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

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
        fun findBundledFrameworksDir(): java.io.File? {
            val codeSource = MpvNative::class.java.protectionDomain?.codeSource?.location ?: return null
            val contentsDir = runCatching { java.io.File(codeSource.toURI()) }.getOrNull()?.parentFile?.parentFile
            return contentsDir?.resolve("Frameworks")?.takeIf { it.exists() && it.isDirectory }
        }

        fun load(): MpvNative {
            val explicit = System.getProperty("vimivo.mpv.library")?.takeIf(String::isNotBlank)
            if (explicit != null) {
                return Native.load(explicit, MpvNative::class.java)
            }
            val frameworks = findBundledFrameworksDir()
            if (frameworks != null) {
                val candidate = listOf("libmpv.2.dylib", "libmpv.dylib")
                    .map { java.io.File(frameworks, it) }
                    .firstOrNull(java.io.File::exists)
                if (candidate != null) {
                    val currentJna = System.getProperty("jna.library.path")
                    if (currentJna.isNullOrBlank()) {
                        System.setProperty("jna.library.path", frameworks.absolutePath)
                    } else if (!currentJna.contains(frameworks.absolutePath)) {
                        System.setProperty("jna.library.path", "$currentJna:${frameworks.absolutePath}")
                    }
                    return Native.load(candidate.absolutePath, MpvNative::class.java)
                }
            }
            return Native.load("mpv", MpvNative::class.java)
        }
    }
}
