#import <Cocoa/Cocoa.h>
#import <OpenGL/OpenGL.h>
#import <OpenGL/gl3.h>
#import <QuartzCore/QuartzCore.h>
#import <MediaPlayer/MediaPlayer.h>
#include <jawt.h>
#include <jawt_md.h>
#include <mpv/client.h>
#include <mpv/render_gl.h>
#include <atomic>

static void *get_proc_address(void *, const char *name) {
    CFStringRef symbol = CFStringCreateWithCString(kCFAllocatorDefault, name, kCFStringEncodingASCII);
    void *address = CFBundleGetFunctionPointerForName(
        CFBundleGetBundleWithIdentifier(CFSTR("com.apple.opengl")), symbol);
    CFRelease(symbol);
    return address;
}

enum class PixelFormatKind { Accelerated, Any, Software };

static CGLPixelFormatObj choosePixelFormat(PixelFormatKind kind) {
    CGLPixelFormatAttribute attributes[12] = {
        kCGLPFAOpenGLProfile, (CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,
        kCGLPFAColorSize, (CGLPixelFormatAttribute)24,
        kCGLPFAAlphaSize, (CGLPixelFormatAttribute)8,
        kCGLPFADoubleBuffer,
    };
    int next = 7;
    if (kind == PixelFormatKind::Accelerated) attributes[next++] = kCGLPFAAccelerated;
    if (kind == PixelFormatKind::Software) {
        attributes[next++] = kCGLPFARendererID;
        attributes[next++] = (CGLPixelFormatAttribute)kCGLRendererGenericFloatID;
    }
    attributes[next] = (CGLPixelFormatAttribute)0;
    CGLPixelFormatObj format = NULL;
    GLint count = 0;
    if (CGLChoosePixelFormat(attributes, &format, &count) != kCGLNoError) return NULL;
    return format;
}

// Hardware OpenGL first. Macs without it (virtual machines, CI runners) get whatever CGL offers, which is Apple's
// software renderer; libmpv reports it as "Apple Software Renderer" and Vimivo switches to performance mode.
// VIMIVO_SOFTWARE_GL=1 forces the software renderer for testing.
static CGLPixelFormatObj createPixelFormat(void) {
    const char *forceSoftware = getenv("VIMIVO_SOFTWARE_GL");
    if (forceSoftware && strcmp(forceSoftware, "1") == 0) return choosePixelFormat(PixelFormatKind::Software);
    CGLPixelFormatObj format = choosePixelFormat(PixelFormatKind::Accelerated);
    return format ? format : choosePixelFormat(PixelFormatKind::Any);
}

// Frames libmpv has rendered into the layer and the size of the last one, so tests can wait for a real frame after a
// window change instead of sleeping.
static std::atomic<long long> presentedFrames{0};
static std::atomic<int> presentedWidth{0};
static std::atomic<int> presentedHeight{0};

// The size of the colour attachment of the framebuffer Core Animation bound for this draw: what mpv must render into.
// The layer's bounds can already describe the next window size while the IOSurface behind the bound framebuffer still
// has the previous one (a fullscreen or resize transition); rendering a bounds-sized picture into it clips or shifts it.
static bool boundFramebufferSize(GLint framebuffer, int *width, int *height) {
    if (framebuffer == 0) return false;
    GLint type = 0, name = 0;
    glGetFramebufferAttachmentParameteriv(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                                          GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, &type);
    glGetFramebufferAttachmentParameteriv(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                                          GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, &name);
    GLint w = 0, h = 0;
    if (type == GL_RENDERBUFFER) {
        GLint previous = 0;
        glGetIntegerv(GL_RENDERBUFFER_BINDING, &previous);
        glBindRenderbuffer(GL_RENDERBUFFER, (GLuint)name);
        glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH, &w);
        glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT, &h);
        glBindRenderbuffer(GL_RENDERBUFFER, (GLuint)previous);
    } else if (type == GL_TEXTURE) {
        // IOSurface-backed layers use rectangle textures.
        GLint previous = 0;
        glGetIntegerv(GL_TEXTURE_BINDING_RECTANGLE, &previous);
        glBindTexture(GL_TEXTURE_RECTANGLE, (GLuint)name);
        glGetTexLevelParameteriv(GL_TEXTURE_RECTANGLE, 0, GL_TEXTURE_WIDTH, &w);
        glGetTexLevelParameteriv(GL_TEXTURE_RECTANGLE, 0, GL_TEXTURE_HEIGHT, &h);
        glBindTexture(GL_TEXTURE_RECTANGLE, (GLuint)previous);
    }
    if (w <= 0 || h <= 0) return false;
    *width = w;
    *height = h;
    return true;
}

static std::atomic<long long> sizeMismatches{0};
// True between a window's will-enter/exit-fullscreen and did-enter/exit notifications. AppKit ignores a fullscreen
// toggle requested while the previous one is animating, so the app waits on this before the next request.
static std::atomic<bool> fullscreenTransitioning{false};

static void observeFullscreenTransitions() {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
        NSNotificationCenter *center = NSNotificationCenter.defaultCenter;
        for (NSNotificationName name in @[NSWindowWillEnterFullScreenNotification, NSWindowWillExitFullScreenNotification]) {
            [center addObserverForName:name object:nil queue:nil usingBlock:^(NSNotification *) { fullscreenTransitioning = true; }];
        }
        for (NSNotificationName name in @[NSWindowDidEnterFullScreenNotification, NSWindowDidExitFullScreenNotification]) {
            [center addObserverForName:name object:nil queue:nil usingBlock:^(NSNotification *) { fullscreenTransitioning = false; }];
        }
    });
}

// libmpv's render context owns GL objects (FBOs, shaders) that belong to the context it was created on,
// so Core Animation must draw with that same context rather than a fresh one per layer.
@interface VimivoMpvLayer : CAOpenGLLayer
@property(nonatomic) mpv_render_context *renderContext;
@property(nonatomic) CGLPixelFormatObj sharedPixelFormat;
@property(nonatomic) CGLContextObj sharedContext;
@end

@implementation VimivoMpvLayer
- (CGLPixelFormatObj)copyCGLPixelFormatForDisplayMask:(uint32_t)mask {
    return CGLRetainPixelFormat(self.sharedPixelFormat);
}

- (CGLContextObj)copyCGLContextForPixelFormat:(CGLPixelFormatObj)pixelFormat {
    return CGLRetainContext(self.sharedContext);
}

- (void)drawInCGLContext:(CGLContextObj)cglContext
             pixelFormat:(CGLPixelFormatObj)pixelFormat
                  forLayerTime:(CFTimeInterval)timeInterval
                   displayTime:(const CVTimeStamp *)timeStamp {
    CGLLockContext(cglContext);
    CGLSetCurrentContext(cglContext);
    const CGFloat scale = self.contentsScale > 0 ? self.contentsScale : 1;
    int width = MAX(1, (int)(self.bounds.size.width * scale));
    int height = MAX(1, (int)(self.bounds.size.height * scale));
    GLint boundFramebuffer = 0;
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &boundFramebuffer);
    int actualWidth = 0, actualHeight = 0;
    if (boundFramebufferSize(boundFramebuffer, &actualWidth, &actualHeight)) {
        if (actualWidth != width || actualHeight != height) {
            sizeMismatches++;
            if (getenv("VIMIVO_RENDER_LOG")) {
                fprintf(stderr, "VIMIVO_RENDER size mismatch: bounds %dx%d, framebuffer %dx%d\n", width, height,
                        actualWidth, actualHeight);
            }
        }
        // Render into what is really there, whatever the bounds say at this instant.
        width = actualWidth;
        height = actualHeight;
    } else if (getenv("VIMIVO_RENDER_LOG")) {
        fprintf(stderr, "VIMIVO_RENDER could not read the framebuffer size (fbo %d)\n", boundFramebuffer);
    }
    glViewport(0, 0, width, height);
    if (self.renderContext) {
        // Core Animation binds the layer's own IOSurface-backed framebuffer before calling this method. It is
        // framebuffer 0 only by accident (a layer created at its final size); rendering into 0 otherwise fails with
        // GL_INVALID_FRAMEBUFFER_OPERATION and the picture stays black while audio plays.
        mpv_opengl_fbo fbo = { .fbo = boundFramebuffer, .w = width, .h = height, .internal_format = 0 };
        int flip = 1;
        mpv_render_param params[] = {
            { MPV_RENDER_PARAM_OPENGL_FBO, &fbo },
            { MPV_RENDER_PARAM_FLIP_Y, &flip },
            { MPV_RENDER_PARAM_INVALID, NULL },
        };
        mpv_render_context_render(self.renderContext, params);
        presentedWidth = width;
        presentedHeight = height;
        presentedFrames++;
    } else {
        glClearColor(0, 0, 0, 1);
        glClear(GL_COLOR_BUFFER_BIT);
    }
    glFlush();
    CGLUnlockContext(cglContext);
}
@end

static VimivoMpvLayer *activeLayer = nil;
static id<JAWT_SurfaceLayers> activeSurface = nil;
static JavaVM *nowPlayingJvm = NULL;
static jobject nowPlayingCallback = NULL;
static id toggleCommandTarget = nil;
static id nextCommandTarget = nil;
static id previousCommandTarget = nil;
static id seekCommandTarget = nil;

static NSString *toNSString(JNIEnv *env, jstring value) {
    if (!value) return nil;
    const char *characters = env->GetStringUTFChars(value, NULL);
    NSString *result = [NSString stringWithUTF8String:characters];
    env->ReleaseStringUTFChars(value, characters);
    return result;
}

static void callNowPlayingCallback(const char *method, const char *signature, jlong value = 0) {
    if (!nowPlayingJvm || !nowPlayingCallback) return;
    JNIEnv *env = NULL;
    bool detach = false;
    if (nowPlayingJvm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (nowPlayingJvm->AttachCurrentThread((void **)&env, NULL) != JNI_OK) return;
        detach = true;
    }
    jclass callbackClass = env->GetObjectClass(nowPlayingCallback);
    jmethodID methodId = env->GetMethodID(callbackClass, method, signature);
    if (methodId) {
        if (signature[1] == 'J') env->CallVoidMethod(nowPlayingCallback, methodId, value);
        else env->CallVoidMethod(nowPlayingCallback, methodId);
    }
    env->DeleteLocalRef(callbackClass);
    if (detach) nowPlayingJvm->DetachCurrentThread();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_vimivo_desktop_integration_macos_MacNowPlayingService_configureNative(JNIEnv *env, jobject self) {
    if (nowPlayingCallback) return JNI_TRUE;
    if (env->GetJavaVM(&nowPlayingJvm) != JNI_OK) return JNI_FALSE;
    nowPlayingCallback = env->NewGlobalRef(self);
    void (^configureCommands)(void) = ^{
        MPRemoteCommandCenter *center = MPRemoteCommandCenter.sharedCommandCenter;
        center.togglePlayPauseCommand.enabled = YES;
        toggleCommandTarget = [center.togglePlayPauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *) {
            callNowPlayingCallback("remoteTogglePlayback", "()V");
            return MPRemoteCommandHandlerStatusSuccess;
        }];
        center.nextTrackCommand.enabled = YES;
        nextCommandTarget = [center.nextTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *) {
            callNowPlayingCallback("remoteNext", "()V");
            return MPRemoteCommandHandlerStatusSuccess;
        }];
        center.previousTrackCommand.enabled = YES;
        previousCommandTarget = [center.previousTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *) {
            callNowPlayingCallback("remotePrevious", "()V");
            return MPRemoteCommandHandlerStatusSuccess;
        }];
        center.changePlaybackPositionCommand.enabled = YES;
        seekCommandTarget = [center.changePlaybackPositionCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent *event) {
            MPChangePlaybackPositionCommandEvent *seekEvent = (MPChangePlaybackPositionCommandEvent *)event;
            callNowPlayingCallback("remoteSeek", "(J)V", (jlong)(seekEvent.positionTime * 1000.0));
            return MPRemoteCommandHandlerStatusSuccess;
        }];
    };
    if (NSThread.isMainThread) configureCommands();
    else dispatch_sync(dispatch_get_main_queue(), configureCommands);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vimivo_desktop_integration_macos_MacNowPlayingService_updateNative(
    JNIEnv *env, jobject, jstring title, jstring artist, jstring album, jlong durationMillis,
    jlong positionMillis, jboolean playing, jfloat rate, jint queueIndex, jint queueCount) {
    NSString *nativeTitle = toNSString(env, title);
    NSString *nativeArtist = toNSString(env, artist);
    NSString *nativeAlbum = toNSString(env, album);
    dispatch_async(dispatch_get_main_queue(), ^{
        if (!nativeTitle) {
            MPNowPlayingInfoCenter.defaultCenter.nowPlayingInfo = nil;
            MPNowPlayingInfoCenter.defaultCenter.playbackState = MPNowPlayingPlaybackStateStopped;
            return;
        }
        NSMutableDictionary *info = [NSMutableDictionary dictionary];
        info[MPMediaItemPropertyTitle] = nativeTitle;
        if (nativeArtist) info[MPMediaItemPropertyArtist] = nativeArtist;
        if (nativeAlbum) info[MPMediaItemPropertyAlbumTitle] = nativeAlbum;
        info[MPMediaItemPropertyPlaybackDuration] = @(MAX(0, durationMillis) / 1000.0);
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(MAX(0, positionMillis) / 1000.0);
        info[MPNowPlayingInfoPropertyPlaybackRate] = playing ? @(rate) : @0.0;
        info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = @1.0;
        info[MPNowPlayingInfoPropertyPlaybackQueueIndex] = @(MAX(0, queueIndex));
        info[MPNowPlayingInfoPropertyPlaybackQueueCount] = @(MAX(0, queueCount));
        MPNowPlayingInfoCenter.defaultCenter.nowPlayingInfo = info;
        MPNowPlayingInfoCenter.defaultCenter.playbackState = playing
            ? MPNowPlayingPlaybackStatePlaying : MPNowPlayingPlaybackStatePaused;
    });
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vimivo_desktop_integration_macos_MacNowPlayingService_closeNative(JNIEnv *env, jobject) {
    void (^removeCommands)(void) = ^{
        MPRemoteCommandCenter *center = MPRemoteCommandCenter.sharedCommandCenter;
        if (toggleCommandTarget) [center.togglePlayPauseCommand removeTarget:toggleCommandTarget];
        if (nextCommandTarget) [center.nextTrackCommand removeTarget:nextCommandTarget];
        if (previousCommandTarget) [center.previousTrackCommand removeTarget:previousCommandTarget];
        if (seekCommandTarget) [center.changePlaybackPositionCommand removeTarget:seekCommandTarget];
        toggleCommandTarget = nextCommandTarget = previousCommandTarget = seekCommandTarget = nil;
        MPNowPlayingInfoCenter.defaultCenter.nowPlayingInfo = nil;
        MPNowPlayingInfoCenter.defaultCenter.playbackState = MPNowPlayingPlaybackStateStopped;
    };
    if (NSThread.isMainThread) removeCommands();
    else dispatch_sync(dispatch_get_main_queue(), removeCommands);
    if (nowPlayingCallback) env->DeleteGlobalRef(nowPlayingCallback);
    nowPlayingCallback = NULL;
    nowPlayingJvm = NULL;
}

static void on_mpv_update(void *context) {
    VimivoMpvLayer *layer = (__bridge VimivoMpvLayer *)context;
    dispatch_async(dispatch_get_main_queue(), ^{ [layer setNeedsDisplay]; });
}

// Bridge-specific failures use values below libmpv's error range so Kotlin can report the exact cause.
static const jint VIMIVO_ATTACH_ALREADY_ATTACHED = -1001;
static const jint VIMIVO_ATTACH_NO_JAWT = -1002;
static const jint VIMIVO_ATTACH_SURFACE_LOCK = -1003;
static const jint VIMIVO_ATTACH_NO_LAYER_HOST = -1004;
static const jint VIMIVO_ATTACH_NO_GL_CONTEXT = -1005;

extern "C" JNIEXPORT jint JNICALL
Java_dev_vimivo_desktop_playback_MpvCocoaRenderer_attachNative(JNIEnv *env, jobject self, jobject canvas, jlong mpvHandle) {
    if (activeLayer || !mpvHandle) return VIMIVO_ATTACH_ALREADY_ATTACHED;
    JAWT awt;
    awt.version = JAWT_VERSION_1_7;
    if (!JAWT_GetAWT(env, &awt)) return VIMIVO_ATTACH_NO_JAWT;
    JAWT_DrawingSurface *surface = awt.GetDrawingSurface(env, canvas);
    if (!surface) return VIMIVO_ATTACH_SURFACE_LOCK;
    if (surface->Lock(surface) & JAWT_LOCK_ERROR) {
        awt.FreeDrawingSurface(surface);
        return VIMIVO_ATTACH_SURFACE_LOCK;
    }
    JAWT_DrawingSurfaceInfo *info = surface->GetDrawingSurfaceInfo(surface);
    id<JAWT_SurfaceLayers> target = info ? (__bridge id<JAWT_SurfaceLayers>)info->platformInfo : nil;
    if (info) surface->FreeDrawingSurfaceInfo(info);
    surface->Unlock(surface);
    awt.FreeDrawingSurface(surface);
    if (!target) return VIMIVO_ATTACH_NO_LAYER_HOST;

    __block int result = MPV_ERROR_GENERIC;
    dispatch_sync(dispatch_get_main_queue(), ^{
        CGLPixelFormatObj pixelFormat = createPixelFormat();
        CGLContextObj glContext = NULL;
        if (!pixelFormat || CGLCreateContext(pixelFormat, NULL, &glContext) != kCGLNoError) {
            if (pixelFormat) CGLReleasePixelFormat(pixelFormat);
            result = VIMIVO_ATTACH_NO_GL_CONTEXT;
            return;
        }
        // mpv_render_context_create requires the OpenGL context to be current on the calling thread.
        CGLContextObj previous = CGLGetCurrentContext();
        CGLSetCurrentContext(glContext);
        mpv_opengl_init_params init = { .get_proc_address = get_proc_address, .get_proc_address_ctx = NULL };
        mpv_render_param params[] = {
            { MPV_RENDER_PARAM_API_TYPE, (void *)MPV_RENDER_API_TYPE_OPENGL },
            { MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, &init },
            { MPV_RENDER_PARAM_INVALID, NULL },
        };
        mpv_render_context *renderContext = NULL;
        result = mpv_render_context_create(&renderContext, (mpv_handle *)mpvHandle, params);
        CGLSetCurrentContext(previous);
        if (result < 0) {
            CGLReleaseContext(glContext);
            CGLReleasePixelFormat(pixelFormat);
            return;
        }
        VimivoMpvLayer *layer = [VimivoMpvLayer layer];
        layer.contentsScale = NSScreen.mainScreen.backingScaleFactor;
        // Draw again when the layer's size changes: a paused video that is resized (or leaves fullscreen) gets no new
        // frame from libmpv, and would otherwise keep the old picture or none until playback resumes.
        layer.needsDisplayOnBoundsChange = YES;
        layer.sharedPixelFormat = pixelFormat;
        layer.sharedContext = glContext;
        layer.renderContext = renderContext;
        mpv_render_context_set_update_callback(layer.renderContext, on_mpv_update, (__bridge void *)layer);
        observeFullscreenTransitions();
        target.layer = layer;
        activeLayer = layer;
        activeSurface = target;
    });
    return result >= 0 ? 0 : result;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vimivo_desktop_playback_MpvCocoaRenderer_detachNative(JNIEnv *, jobject) {
    dispatch_sync(dispatch_get_main_queue(), ^{
        // Keep the layer alive locally: clearing the host's layer drops what may be the last reference.
        VimivoMpvLayer *layer = activeLayer;
        if (activeSurface) activeSurface.layer = nil;
        CGLContextObj glContext = layer.sharedContext;
        if (layer.renderContext) {
            // The render context must be freed with its OpenGL context current.
            CGLContextObj previous = CGLGetCurrentContext();
            if (glContext) {
                CGLLockContext(glContext);
                CGLSetCurrentContext(glContext);
            }
            mpv_render_context_set_update_callback(layer.renderContext, NULL, NULL);
            mpv_render_context_free(layer.renderContext);
            layer.renderContext = NULL;
            CGLSetCurrentContext(previous);
            if (glContext) CGLUnlockContext(glContext);
        }
        if (glContext) CGLReleaseContext(glContext);
        if (layer.sharedPixelFormat) CGLReleasePixelFormat(layer.sharedPixelFormat);
        layer.sharedContext = NULL;
        layer.sharedPixelFormat = NULL;
        activeLayer = nil;
        activeSurface = nil;
    });
}

// ---- Window helpers for the player's chrome and overlays (dev.vimivo.desktop.platform.MacWindows) ----

// The NSWindow behind an AWT window, found from the CALayer JAWT exposes for it: walking up from that layer reaches
// the window's content or frame view layer. Works on any JDK, without private AWT classes.
static NSWindow *nativeWindowFor(JNIEnv *env, jobject component) {
    JAWT awt;
    awt.version = JAWT_VERSION_1_7;
    if (!JAWT_GetAWT(env, &awt)) return nil;
    JAWT_DrawingSurface *surface = awt.GetDrawingSurface(env, component);
    if (!surface) return nil;
    if (surface->Lock(surface) & JAWT_LOCK_ERROR) {
        awt.FreeDrawingSurface(surface);
        return nil;
    }
    JAWT_DrawingSurfaceInfo *info = surface->GetDrawingSurfaceInfo(surface);
    id<JAWT_SurfaceLayers> layers = info ? (__bridge id<JAWT_SurfaceLayers>)info->platformInfo : nil;
    CALayer *windowLayer = layers.windowLayer;
    if (info) surface->FreeDrawingSurfaceInfo(info);
    surface->Unlock(surface);
    awt.FreeDrawingSurface(surface);
    if (!windowLayer) return nil;
    __block NSWindow *found = nil;
    void (^search)(void) = ^{
        for (NSWindow *window in NSApp.windows) {
            for (CALayer *layer = windowLayer; layer && !found; layer = layer.superlayer) {
                if (layer == window.contentView.layer || layer == window.contentView.superview.layer) found = window;
            }
            if (found) return;
        }
    };
    if (NSThread.isMainThread) search();
    else dispatch_sync(dispatch_get_main_queue(), search);
    return found;
}

// Dark title bar and controls for one window (JDK 17 has no per-window appearance property).
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_vimivo_desktop_platform_MacWindows_setDarkAppearanceNative(JNIEnv *env, jobject, jobject window) {
    NSWindow *nativeWindow = nativeWindowFor(env, window);
    if (!nativeWindow) return JNI_FALSE;
    dispatch_async(dispatch_get_main_queue(), ^{
        nativeWindow.appearance = [NSAppearance appearanceNamed:NSAppearanceNameDarkAqua];
    });
    return JNI_TRUE;
}

// Makes an overlay a real child window: it moves with its owner and appears in the owner's fullscreen space.
extern "C" JNIEXPORT jboolean JNICALL
Java_dev_vimivo_desktop_platform_MacWindows_attachChildNative(JNIEnv *env, jobject, jobject owner, jobject child) {
    NSWindow *parent = nativeWindowFor(env, owner);
    NSWindow *overlay = nativeWindowFor(env, child);
    if (!parent || !overlay || parent == overlay) return JNI_FALSE;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (overlay.parentWindow != parent) [parent addChildWindow:overlay ordered:NSWindowAbove];
        overlay.collectionBehavior |= NSWindowCollectionBehaviorFullScreenAuxiliary;
    });
    return JNI_TRUE;
}

// Brings Vimivo to the front; macOS sends mouse-moved (hover) events only to the active app. Used by --ui-capture.
extern "C" JNIEXPORT void JNICALL
Java_dev_vimivo_desktop_platform_MacWindows_activateNative(JNIEnv *, jobject) {
    dispatch_async(dispatch_get_main_queue(), ^{ [NSApp activateIgnoringOtherApps:YES]; });
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_dev_vimivo_desktop_playback_MpvCocoaRenderer_presentedFrameNative(JNIEnv *env, jobject) {
    if (getenv("VIMIVO_RENDER_LOG")) {
        // Read off the main thread on purpose: this is a diagnostic and must not wait for AppKit.
        static int lastState = -1;
        VimivoMpvLayer *layer = activeLayer;
        id<JAWT_SurfaceLayers> surface = activeSurface;
        const int state = (layer ? 1 : 0) | (layer.superlayer ? 2 : 0) | ((surface && surface.layer == layer) ? 4 : 0);
        if (state != lastState) {
            fprintf(stderr, "VIMIVO_RENDER layer state %d (1 exists, 2 has superlayer, 4 is the host's layer)\n", state);
            lastState = state;
        }
    }
    jlong values[4] = { presentedFrames.load(), presentedWidth.load(), presentedHeight.load(), sizeMismatches.load() };
    jlongArray result = env->NewLongArray(4);
    if (result) env->SetLongArrayRegion(result, 0, 4, values);
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_vimivo_desktop_playback_MpvCocoaRenderer_windowTransitioningNative(JNIEnv *, jobject) {
    return fullscreenTransitioning.load() ? JNI_TRUE : JNI_FALSE;
}
