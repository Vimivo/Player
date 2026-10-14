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
    fun mpv_command(context: Pointer, arguments: Pointer): Int
    fun mpv_error_string(code: Int): String?

    companion object {
        fun load(): MpvNative {
            val explicit = System.getProperty("vimivo.mpv.library")?.takeIf(String::isNotBlank)
            return Native.load(explicit ?: "mpv", MpvNative::class.java)
        }
    }
}
