@file:Suppress("FunctionNaming", "TooManyFunctions") // JNI entry points mirror vimivo_mpv_jni.c.

package dev.vimivo.platform.android.playback

/**
 * JNI bridge to the LGPL libmpv (ADR-005), implemented in `src/main/cpp/vimivo_mpv_jni.c`. Handles are opaque
 * `mpv_handle` pointers; every call is the documented libmpv client API.
 */
internal object LibmpvNative {
    /** True when libvimivo_mpv.so and the media package's libraries loaded (absent in unit tests, for example). */
    val available: Boolean by lazy {
        runCatching { System.loadLibrary("vimivo_mpv") }.isSuccess
    }

    @JvmStatic external fun create(): Long

    @JvmStatic external fun initialize(handle: Long): Int

    @JvmStatic external fun destroy(handle: Long)

    @JvmStatic external fun setOption(handle: Long, name: String, value: String): Int

    @JvmStatic external fun setProperty(handle: Long, name: String, value: String): Int

    @JvmStatic external fun getProperty(handle: Long, name: String): String?

    @JvmStatic external fun command(handle: Long, arguments: Array<String>): Int

    /** [event id, error, end-file reason, end-file error]; event id 0 when [timeoutSeconds] passed. */
    @JvmStatic external fun waitEvent(handle: Long, timeoutSeconds: Double): IntArray

    @JvmStatic external fun wakeup(handle: Long)

    @JvmStatic external fun errorString(error: Int): String

    /** Sets libmpv's `wid` to a new global reference of [surface]; returns it (0 on failure) for [releaseSurface]. */
    @JvmStatic external fun attachSurface(handle: Long, surface: android.view.Surface): Long

    @JvmStatic external fun releaseSurface(handle: Long, reference: Long)
}
