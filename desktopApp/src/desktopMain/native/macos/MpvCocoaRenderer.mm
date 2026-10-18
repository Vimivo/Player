#import <Cocoa/Cocoa.h>
#import <OpenGL/OpenGL.h>
#import <OpenGL/gl3.h>
#import <QuartzCore/QuartzCore.h>
#import <MediaPlayer/MediaPlayer.h>
#include <jawt.h>
#include <jawt_md.h>
#include <mpv/client.h>
#include <mpv/render_gl.h>

static void *get_proc_address(void *, const char *name) {
    CFStringRef symbol = CFStringCreateWithCString(kCFAllocatorDefault, name, kCFStringEncodingASCII);
    void *address = CFBundleGetFunctionPointerForName(
        CFBundleGetBundleWithIdentifier(CFSTR("com.apple.opengl")), symbol);
    CFRelease(symbol);
    return address;
}

static CGLPixelFormatObj createPixelFormat(void) {
    CGLPixelFormatAttribute attributes[] = {
        kCGLPFAOpenGLProfile, (CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,
        kCGLPFAColorSize, (CGLPixelFormatAttribute)24,
        kCGLPFAAlphaSize, (CGLPixelFormatAttribute)8,
        kCGLPFAAccelerated,
        kCGLPFADoubleBuffer, (CGLPixelFormatAttribute)0,
    };
    CGLPixelFormatObj format = NULL;
    GLint count = 0;
    if (CGLChoosePixelFormat(attributes, &format, &count) != kCGLNoError) return NULL;
    return format;
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
    const int width = MAX(1, (int)(self.bounds.size.width * scale));
    const int height = MAX(1, (int)(self.bounds.size.height * scale));
    glViewport(0, 0, width, height);
    if (self.renderContext) {
        mpv_opengl_fbo fbo = { .fbo = 0, .w = width, .h = height, .internal_format = 0 };
        int flip = 1;
        mpv_render_param params[] = {
            { MPV_RENDER_PARAM_OPENGL_FBO, &fbo },
            { MPV_RENDER_PARAM_FLIP_Y, &flip },
            { MPV_RENDER_PARAM_INVALID, NULL },
        };
        mpv_render_context_render(self.renderContext, params);
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
        layer.sharedPixelFormat = pixelFormat;
        layer.sharedContext = glContext;
        layer.renderContext = renderContext;
        mpv_render_context_set_update_callback(layer.renderContext, on_mpv_update, (__bridge void *)layer);
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
