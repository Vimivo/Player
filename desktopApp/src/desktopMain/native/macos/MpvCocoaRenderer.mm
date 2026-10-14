#import <Cocoa/Cocoa.h>
#import <OpenGL/OpenGL.h>
#import <OpenGL/gl3.h>
#import <QuartzCore/QuartzCore.h>
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

@interface VimivoMpvLayer : CAOpenGLLayer
@property(nonatomic) mpv_render_context *renderContext;
@end

@implementation VimivoMpvLayer
- (CGLPixelFormatObj)copyCGLPixelFormatForDisplayMask:(uint32_t)mask {
    CGLPixelFormatAttribute attributes[] = {
        kCGLPFAOpenGLProfile, (CGLPixelFormatAttribute)kCGLOGLPVersion_3_2_Core,
        kCGLPFAColorSize, (CGLPixelFormatAttribute)24,
        kCGLPFAAlphaSize, (CGLPixelFormatAttribute)8,
        kCGLPFADoubleBuffer, (CGLPixelFormatAttribute)0,
    };
    CGLPixelFormatObj format = NULL;
    GLint count = 0;
    CGLChoosePixelFormat(attributes, &format, &count);
    return format;
}

- (CGLContextObj)copyCGLContextForPixelFormat:(CGLPixelFormatObj)pixelFormat {
    CGLContextObj context = NULL;
    CGLCreateContext(pixelFormat, NULL, &context);
    return context;
}

- (void)drawInCGLContext:(CGLContextObj)cglContext
             pixelFormat:(CGLPixelFormatObj)pixelFormat
                  forLayerTime:(CFTimeInterval)timeInterval
                   displayTime:(const CVTimeStamp *)timeStamp {
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
}
@end

static VimivoMpvLayer *activeLayer = nil;
static id<JAWT_SurfaceLayers> activeSurface = nil;

static void on_mpv_update(void *context) {
    VimivoMpvLayer *layer = (__bridge VimivoMpvLayer *)context;
    dispatch_async(dispatch_get_main_queue(), ^{ [layer setNeedsDisplay]; });
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_vimivo_desktop_playback_MpvCocoaRenderer_attachNative(JNIEnv *env, jobject self, jobject canvas, jlong mpvHandle) {
    if (activeLayer || !mpvHandle) return JNI_FALSE;
    JAWT awt;
    awt.version = JAWT_VERSION_1_7;
    if (!JAWT_GetAWT(env, &awt)) return JNI_FALSE;
    JAWT_DrawingSurface *surface = awt.GetDrawingSurface(env, canvas);
    if (!surface || surface->Lock(surface) & JAWT_LOCK_ERROR) return JNI_FALSE;
    JAWT_DrawingSurfaceInfo *info = surface->GetDrawingSurfaceInfo(surface);
    id<JAWT_SurfaceLayers> target = info ? (__bridge id<JAWT_SurfaceLayers>)info->platformInfo : nil;
    if (info) surface->FreeDrawingSurfaceInfo(info);
    surface->Unlock(surface);
    awt.FreeDrawingSurface(surface);
    if (!target) return JNI_FALSE;

    __block int result = MPV_ERROR_GENERIC;
    dispatch_sync(dispatch_get_main_queue(), ^{
        VimivoMpvLayer *layer = [VimivoMpvLayer layer];
        layer.contentsScale = NSScreen.mainScreen.backingScaleFactor;
        mpv_opengl_init_params init = { .get_proc_address = get_proc_address, .get_proc_address_ctx = NULL };
        mpv_render_param params[] = {
            { MPV_RENDER_PARAM_API_TYPE, (void *)MPV_RENDER_API_TYPE_OPENGL },
            { MPV_RENDER_PARAM_OPENGL_INIT_PARAMS, &init },
            { MPV_RENDER_PARAM_INVALID, NULL },
        };
        mpv_render_context *renderContext = NULL;
        result = mpv_render_context_create(&renderContext, (mpv_handle *)mpvHandle, params);
        if (result >= 0) {
            layer.renderContext = renderContext;
            mpv_render_context_set_update_callback(layer.renderContext, on_mpv_update, (__bridge void *)layer);
            target.layer = layer;
            activeLayer = layer;
            activeSurface = target;
        }
    });
    return result >= 0 ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vimivo_desktop_playback_MpvCocoaRenderer_detachNative(JNIEnv *, jobject) {
    dispatch_sync(dispatch_get_main_queue(), ^{
        if (activeSurface) activeSurface.layer = nil;
        if (activeLayer.renderContext) {
            mpv_render_context_set_update_callback(activeLayer.renderContext, NULL, NULL);
            mpv_render_context_free(activeLayer.renderContext);
            activeLayer.renderContext = NULL;
        }
        activeLayer = nil;
        activeSurface = nil;
    });
}
