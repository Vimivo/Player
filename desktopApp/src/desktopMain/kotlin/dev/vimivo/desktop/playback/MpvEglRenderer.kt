@file:Suppress("FunctionNaming", "MagicNumber") // C names and EGL/libmpv constants mirror the native headers.

package dev.vimivo.desktop.playback

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.platform.unix.X11
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.awt.Canvas
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Linux video output through the libmpv render API into Vimivo's own EGL context on the canvas's X11 window
 * (XWayland on Wayland sessions), the same model as macOS.
 *
 * mpv's X11 code is GPL, so the LGPL libmpv (ADR-004) has no X11 video output and cannot embed with `wid`.
 * Everything X11 and EGL here is Vimivo's own code over JNA; libmpv only renders into the bound framebuffer.
 * VA-API reaches the GPU through a DRM render node handed to libmpv. All EGL and render-context calls happen on one
 * dedicated thread.
 */
internal class MpvEglRenderer : DesktopVideoRenderer {
    private var thread: RenderThread? = null

    override fun attach(canvas: Canvas, context: Pointer) {
        check(thread == null) { "A render target is already attached" }
        check(canvas.isDisplayable) { "The video surface is not displayable yet" }
        val xid = runCatching { Native.getComponentID(canvas) }.getOrDefault(0L)
        check(xid != 0L) { "Unable to obtain the native X11 window of the video surface" }
        val started = RenderThread(xid, context)
        started.start()
        started.awaitReady()
        thread = started
    }

    override fun detach() {
        thread?.shutdown()
        thread = null
    }

    override fun presentedFrame(): PresentedFrame? = thread?.presented

    private class RenderThread(private val window: Long, private val mpvHandle: Pointer) : Thread("vimivo-mpv-egl") {
        private val mpv = MpvNative.load()
        private val ready = CountDownLatch(1)
        private val lock = ReentrantLock()
        private val wake = lock.newCondition()

        @Volatile private var running = true

        @Volatile private var failure: Throwable? = null
        private var pendingUpdate = true

        @Volatile var presented: PresentedFrame = PresentedFrame(0, 0, 0)
            private set

        // Strong references: libmpv calls these from its own threads for as long as the render context lives.
        private val getProcAddress = GetProcAddress { _, name -> Egl.lib.eglGetProcAddress(name) }
        private val onUpdate = UpdateCallback { _ -> signal() }

        init {
            isDaemon = true
        }

        fun awaitReady() {
            check(ready.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "The EGL video output did not start in time" }
            failure?.let { throw IllegalStateException("Unable to start the EGL video output: ${it.message}", it) }
        }

        fun shutdown() {
            running = false
            signal()
            join(TimeUnit.SECONDS.toMillis(READY_TIMEOUT_SECONDS))
        }

        private fun signal() = lock.withLock {
            pendingUpdate = true
            wake.signalAll()
        }

        override fun run() {
            val target = try {
                EglTarget.create(window)
            } catch (error: IllegalStateException) {
                failure = error
                ready.countDown()
                return
            }
            var renderContext: Pointer? = null
            val renderNode = DrmRenderNode.open()
            try {
                renderContext = createRenderContext(renderNode)
                ready.countDown()
                renderLoop(target, renderContext)
            } catch (error: IllegalStateException) {
                failure = error
                ready.countDown()
            } finally {
                renderContext?.let { context ->
                    mpv.mpv_render_context_set_update_callback(context, null, null)
                    mpv.mpv_render_context_free(context)
                }
                renderNode?.close()
                target.release()
            }
        }

        private fun createRenderContext(renderNode: DrmRenderNode?): Pointer {
            val apiType = Memory(7).apply { setString(0, "opengl") }
            val initParams = Memory(Native.POINTER_SIZE * 2L).apply {
                setPointer(0, CallbackPointer.of(getProcAddress))
                setPointer(Native.POINTER_SIZE.toLong(), null)
            }
            val params = mutableListOf(
                MPV_RENDER_PARAM_API_TYPE to apiType,
                MPV_RENDER_PARAM_OPENGL_INIT_PARAMS to initParams,
            )
            renderNode?.let { params += MPV_RENDER_PARAM_DRM_DISPLAY_V2 to it.drmParams }
            val result = PointerByReference()
            val code = mpv.mpv_render_context_create(result, mpvHandle, RenderParams.of(params))
            check(code >= 0) { "libmpv render context error $code" }
            val context = checkNotNull(result.value) { "libmpv returned no render context" }
            mpv.mpv_render_context_set_update_callback(context, CallbackPointer.of(onUpdate), null)
            return context
        }

        private fun renderLoop(target: EglTarget, renderContext: Pointer) {
            var lastSize = 0 to 0
            val fbo = Memory(16)
            val flipY = Memory(4).apply { setInt(0, 1) }
            while (running) {
                lock.withLock {
                    if (!pendingUpdate && running) wake.await(POLL_MILLIS, TimeUnit.MILLISECONDS)
                    pendingUpdate = false
                }
                // A resize redraws the current frame even while paused; libmpv asks for new frames itself.
                val size = target.size()
                val flags = mpv.mpv_render_context_update(renderContext)
                val needsFrame = flags and MPV_RENDER_UPDATE_FRAME != 0L || size != lastSize
                lastSize = size
                val visible = size.first > 0 && size.second > 0
                if (running && needsFrame && visible) {
                    draw(target, renderContext, size, fbo, flipY)
                }
            }
        }

        /** Renders into the window's default framebuffer (mpv_opengl_fbo { fbo, w, h, internal_format }). */
        private fun draw(target: EglTarget, renderContext: Pointer, size: Pair<Int, Int>, fbo: Memory, flipY: Memory) {
            fbo.setInt(0, 0)
            fbo.setInt(4, size.first)
            fbo.setInt(8, size.second)
            fbo.setInt(12, 0)
            val params = RenderParams.of(
                listOf(
                    MPV_RENDER_PARAM_OPENGL_FBO to fbo,
                    MPV_RENDER_PARAM_FLIP_Y to flipY,
                ),
            )
            mpv.mpv_render_context_render(renderContext, params)
            target.swap()
            mpv.mpv_render_context_report_swap(renderContext)
            presented = PresentedFrame(presented.count + 1, size.first, size.second)
        }
    }

    /** An EGL window surface and context on the canvas's X11 window, through Vimivo's own X connection. */
    private class EglTarget(
        private val x: X11.Display,
        private val window: X11.Window,
        private val display: Pointer,
        private val surface: Pointer,
        private val context: Pointer,
    ) {
        private val attributes = X11.XWindowAttributes()

        fun size(): Pair<Int, Int> {
            if (X11.INSTANCE.XGetWindowAttributes(x, window, attributes) == 0) return 0 to 0
            return attributes.width to attributes.height
        }

        fun swap() {
            Egl.lib.eglSwapBuffers(display, surface)
        }

        fun release() {
            Egl.lib.eglMakeCurrent(display, null, null, null)
            Egl.lib.eglDestroySurface(display, surface)
            Egl.lib.eglDestroyContext(display, context)
            Egl.lib.eglTerminate(display)
            X11.INSTANCE.XCloseDisplay(x)
        }

        companion object {
            fun create(xid: Long): EglTarget {
                val x = checkNotNull(X11.INSTANCE.XOpenDisplay(null)) { "cannot connect to the X server" }
                val window = X11.Window(xid)
                val egl = Egl.lib
                // eglGetPlatformDisplay is EGL 1.5; older libEGL builds only have eglGetDisplay.
                val display = runCatching { egl.eglGetPlatformDisplay(EGL_PLATFORM_X11_KHR, x.pointer, null) }
                    .getOrNull() ?: egl.eglGetDisplay(x.pointer)
                val initialized = display != null && egl.eglInitialize(display, IntByReference(), IntByReference()) != 0
                if (!initialized) {
                    X11.INSTANCE.XCloseDisplay(x)
                    error("EGL is unavailable on this X server")
                }
                checkNotNull(display)
                val api = if (egl.eglBindAPI(EGL_OPENGL_API) != 0) {
                    EGL_OPENGL_BIT
                } else {
                    check(egl.eglBindAPI(EGL_OPENGL_ES_API) != 0) { "neither OpenGL nor OpenGL ES is available" }
                    EGL_OPENGL_ES2_BIT
                }
                val config = chooseConfig(display, x, window, api)
                val contextAttributes = if (api == EGL_OPENGL_ES2_BIT) {
                    intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE)
                } else {
                    intArrayOf(EGL_NONE)
                }
                val context = checkNotNull(egl.eglCreateContext(display, config, null, contextAttributes)) {
                    "eglCreateContext failed (0x${Integer.toHexString(egl.eglGetError())})"
                }
                val surface = checkNotNull(egl.eglCreateWindowSurface(display, config, NativeLong(xid), null)) {
                    "eglCreateWindowSurface failed (0x${Integer.toHexString(egl.eglGetError())})"
                }
                check(egl.eglMakeCurrent(display, surface, surface, context) != 0) {
                    "eglMakeCurrent failed (0x${Integer.toHexString(egl.eglGetError())})"
                }
                egl.eglSwapInterval(display, 1)
                return EglTarget(x, window, display, surface, context)
            }

            /** A config whose native visual matches the AWT window's, so the surface can be created on it. */
            private fun chooseConfig(display: Pointer, x: X11.Display, window: X11.Window, api: Int): Pointer {
                val attributes = intArrayOf(
                    EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
                    EGL_RED_SIZE, 8, EGL_GREEN_SIZE, 8, EGL_BLUE_SIZE, 8,
                    EGL_RENDERABLE_TYPE, api,
                    EGL_NONE,
                )
                val configs = arrayOfNulls<Pointer>(MAX_CONFIGS)
                val count = IntByReference()
                val chosen = Egl.lib.eglChooseConfig(display, attributes, configs, MAX_CONFIGS, count) != 0
                check(chosen && count.value > 0) { "no suitable EGL configuration" }
                val windowAttributes = X11.XWindowAttributes()
                X11.INSTANCE.XGetWindowAttributes(x, window, windowAttributes)
                val visualId = windowAttributes.visual?.visualID?.toLong()
                val visual = IntByReference()
                return configs.take(count.value).filterNotNull().firstOrNull { config ->
                    Egl.lib.eglGetConfigAttrib(display, config, EGL_NATIVE_VISUAL_ID, visual) != 0 &&
                        visual.value.toLong() == visualId
                } ?: checkNotNull(configs[0])
            }
        }
    }

    /** A DRM render node for VA-API interop; optional (software decoding without it). */
    private class DrmRenderNode(private val fd: Int) {
        // mpv_opengl_drm_params_v2 { int fd; int crtc_id; int connector_id; void **atomic_request_ptr; int render_fd; }
        val drmParams: Memory = Memory(32).apply {
            clear()
            setInt(0, -1)
            setPointer(16, null)
            setInt(24, fd)
        }

        fun close() {
            LibC.lib.close(fd)
        }

        companion object {
            fun open(): DrmRenderNode? {
                val node = File("/dev/dri").listFiles { file -> file.name.startsWith("renderD") }
                    ?.minByOrNull { it.name } ?: return null
                val fd = runCatching { LibC.lib.open(node.path, O_RDWR or O_CLOEXEC) }.getOrDefault(-1)
                return if (fd >= 0) DrmRenderNode(fd) else null
            }
        }
    }

    /** A null-terminated mpv_render_param array ({ int type; void *data; }). */
    private object RenderParams {
        fun of(params: List<Pair<Int, Pointer>>): Memory {
            val entry = Native.POINTER_SIZE * 2L
            val memory = Memory(entry * (params.size + 1))
            memory.clear()
            params.forEachIndexed { index, (type, data) ->
                memory.setInt(index * entry, type)
                memory.setPointer(index * entry + Native.POINTER_SIZE, data)
            }
            return memory
        }
    }

    private object CallbackPointer {
        fun of(callback: Callback): Pointer = com.sun.jna.CallbackReference.getFunctionPointer(callback)
    }

    private fun interface GetProcAddress : Callback {
        fun invoke(context: Pointer?, name: String): Pointer?
    }

    private fun interface UpdateCallback : Callback {
        fun invoke(context: Pointer?)
    }

    private interface EglLibrary : Library {
        fun eglGetPlatformDisplay(platform: Int, nativeDisplay: Pointer, attributes: Pointer?): Pointer?
        fun eglGetDisplay(nativeDisplay: Pointer): Pointer?
        fun eglInitialize(display: Pointer, major: IntByReference, minor: IntByReference): Int
        fun eglBindAPI(api: Int): Int
        fun eglChooseConfig(
            display: Pointer,
            attributes: IntArray,
            configs: Array<Pointer?>,
            size: Int,
            count: IntByReference
        ): Int
        fun eglGetConfigAttrib(display: Pointer, config: Pointer, attribute: Int, value: IntByReference): Int
        fun eglCreateContext(display: Pointer, config: Pointer, share: Pointer?, attributes: IntArray): Pointer?
        fun eglCreateWindowSurface(
            display: Pointer,
            config: Pointer,
            window: NativeLong,
            attributes: Pointer?
        ): Pointer?
        fun eglMakeCurrent(display: Pointer, draw: Pointer?, read: Pointer?, context: Pointer?): Int
        fun eglSwapInterval(display: Pointer, interval: Int): Int
        fun eglSwapBuffers(display: Pointer, surface: Pointer): Int
        fun eglGetProcAddress(name: String): Pointer?
        fun eglGetError(): Int
        fun eglDestroySurface(display: Pointer, surface: Pointer): Int
        fun eglDestroyContext(display: Pointer, context: Pointer): Int
        fun eglTerminate(display: Pointer): Int
    }

    private object Egl {
        val lib: EglLibrary by lazy { Native.load("EGL", EglLibrary::class.java) }
    }

    private interface LibCLibrary : Library {
        fun open(path: String, flags: Int): Int
        fun close(fd: Int): Int
    }

    private object LibC {
        val lib: LibCLibrary by lazy { Native.load("c", LibCLibrary::class.java) }
    }

    private companion object {
        const val READY_TIMEOUT_SECONDS = 10L
        const val POLL_MILLIS = 50L
        const val MAX_CONFIGS = 64

        const val MPV_RENDER_PARAM_API_TYPE = 1
        const val MPV_RENDER_PARAM_OPENGL_INIT_PARAMS = 2
        const val MPV_RENDER_PARAM_OPENGL_FBO = 3
        const val MPV_RENDER_PARAM_FLIP_Y = 4
        const val MPV_RENDER_PARAM_DRM_DISPLAY_V2 = 16
        const val MPV_RENDER_UPDATE_FRAME = 1L

        const val EGL_PLATFORM_X11_KHR = 0x31D5
        const val EGL_OPENGL_API = 0x30A2
        const val EGL_OPENGL_ES_API = 0x30A0
        const val EGL_OPENGL_BIT = 0x0008
        const val EGL_OPENGL_ES2_BIT = 0x0004
        const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
        const val EGL_SURFACE_TYPE = 0x3033
        const val EGL_WINDOW_BIT = 0x0004
        const val EGL_RED_SIZE = 0x3024
        const val EGL_GREEN_SIZE = 0x3023
        const val EGL_BLUE_SIZE = 0x3022
        const val EGL_RENDERABLE_TYPE = 0x3040
        const val EGL_NATIVE_VISUAL_ID = 0x302E
        const val EGL_NONE = 0x3038

        const val O_RDWR = 0x2
        const val O_CLOEXEC = 0x80000
    }
}
