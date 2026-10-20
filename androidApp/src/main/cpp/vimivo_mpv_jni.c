// Vimivo's JNI bridge to libmpv on Android (ADR-005). Only the documented libmpv client API; no shell, no filename
// interpolation. Kotlin side: dev.vimivo.platform.android.playback.LibmpvNative.
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <mpv/client.h>

// libavcodec/jni.h: hands FFmpeg the Java VM for its MediaCodec decoders (declared here; one function only).
int av_jni_set_java_vm(void *vm, void *log_ctx);

#define JNI_FN(name) Java_dev_vimivo_platform_android_playback_LibmpvNative_##name

// FFmpeg's MediaCodec decoders call back into Java; they need the VM before any decoder opens.
JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    av_jni_set_java_vm(vm, NULL);
    return JNI_VERSION_1_6;
}

static mpv_handle *handle_of(jlong handle) { return (mpv_handle *)(intptr_t)handle; }

JNIEXPORT jlong JNICALL JNI_FN(create)(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return (jlong)(intptr_t)mpv_create();
}

JNIEXPORT jint JNICALL JNI_FN(initialize)(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    return mpv_initialize(handle_of(handle));
}

JNIEXPORT void JNICALL JNI_FN(destroy)(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    mpv_terminate_destroy(handle_of(handle));
}

static jint with_two_strings(JNIEnv *env, jstring a, jstring b, int (*call)(mpv_handle *, const char *, const char *),
                             mpv_handle *handle) {
    const char *first = (*env)->GetStringUTFChars(env, a, NULL);
    const char *second = (*env)->GetStringUTFChars(env, b, NULL);
    int result = (first && second) ? call(handle, first, second) : MPV_ERROR_NOMEM;
    if (first) (*env)->ReleaseStringUTFChars(env, a, first);
    if (second) (*env)->ReleaseStringUTFChars(env, b, second);
    return result;
}

JNIEXPORT jint JNICALL JNI_FN(setOption)(JNIEnv *env, jclass cls, jlong handle, jstring name, jstring value) {
    (void)cls;
    return with_two_strings(env, name, value, mpv_set_option_string, handle_of(handle));
}

JNIEXPORT jint JNICALL JNI_FN(setProperty)(JNIEnv *env, jclass cls, jlong handle, jstring name, jstring value) {
    (void)cls;
    return with_two_strings(env, name, value, mpv_set_property_string, handle_of(handle));
}

JNIEXPORT jstring JNICALL JNI_FN(getProperty)(JNIEnv *env, jclass cls, jlong handle, jstring name) {
    (void)cls;
    const char *property = (*env)->GetStringUTFChars(env, name, NULL);
    if (!property) return NULL;
    char *value = mpv_get_property_string(handle_of(handle), property);
    (*env)->ReleaseStringUTFChars(env, name, property);
    if (!value) return NULL;
    jstring result = (*env)->NewStringUTF(env, value);
    mpv_free(value);
    return result;
}

// Each argument is passed separately to mpv_command: a path is never parsed as a command string.
JNIEXPORT jint JNICALL JNI_FN(command)(JNIEnv *env, jclass cls, jlong handle, jobjectArray arguments) {
    (void)cls;
    jsize count = (*env)->GetArrayLength(env, arguments);
    const char **args = calloc((size_t)count + 1, sizeof(char *));
    jstring *strings = calloc((size_t)count, sizeof(jstring));
    if (!args || !strings) {
        free(args);
        free(strings);
        return MPV_ERROR_NOMEM;
    }
    int result = 0;
    for (jsize i = 0; i < count; i++) {
        strings[i] = (jstring)(*env)->GetObjectArrayElement(env, arguments, i);
        args[i] = strings[i] ? (*env)->GetStringUTFChars(env, strings[i], NULL) : NULL;
        if (!args[i]) result = MPV_ERROR_INVALID_PARAMETER;
    }
    if (result == 0) result = mpv_command(handle_of(handle), args);
    for (jsize i = 0; i < count; i++) {
        if (args[i]) (*env)->ReleaseStringUTFChars(env, strings[i], args[i]);
        if (strings[i]) (*env)->DeleteLocalRef(env, strings[i]);
    }
    free(args);
    free(strings);
    return result;
}

// [event id, error, end-file reason, end-file error]; event id 0 (MPV_EVENT_NONE) on timeout.
JNIEXPORT jintArray JNICALL JNI_FN(waitEvent)(JNIEnv *env, jclass cls, jlong handle, jdouble timeout) {
    (void)cls;
    mpv_event *event = mpv_wait_event(handle_of(handle), timeout);
    jint values[4] = { (jint)event->event_id, event->error, 0, 0 };
    if (event->event_id == MPV_EVENT_END_FILE && event->data) {
        mpv_event_end_file *end = event->data;
        values[2] = end->reason;
        values[3] = end->error;
    }
    jintArray result = (*env)->NewIntArray(env, 4);
    if (result) (*env)->SetIntArrayRegion(env, result, 0, 4, values);
    return result;
}

JNIEXPORT void JNICALL JNI_FN(wakeup)(JNIEnv *env, jclass cls, jlong handle) {
    (void)env; (void)cls;
    mpv_wakeup(handle_of(handle));
}

JNIEXPORT jstring JNICALL JNI_FN(errorString)(JNIEnv *env, jclass cls, jint error) {
    (void)cls;
    return (*env)->NewStringUTF(env, mpv_error_string(error));
}

// libmpv's Android GPU context renders into this Surface ("wid" holds a JNI global reference to it). Returns the
// global reference so the caller can release it after libmpv has stopped using it.
JNIEXPORT jlong JNICALL JNI_FN(attachSurface)(JNIEnv *env, jclass cls, jlong handle, jobject surface) {
    (void)cls;
    jobject global = (*env)->NewGlobalRef(env, surface);
    int64_t wid = (int64_t)(intptr_t)global;
    if (mpv_set_property(handle_of(handle), "wid", MPV_FORMAT_INT64, &wid) < 0) {
        (*env)->DeleteGlobalRef(env, global);
        return 0;
    }
    return (jlong)(intptr_t)global;
}

JNIEXPORT void JNICALL JNI_FN(releaseSurface)(JNIEnv *env, jclass cls, jlong handle, jlong reference) {
    (void)cls;
    int64_t none = 0;
    mpv_set_property(handle_of(handle), "wid", MPV_FORMAT_INT64, &none);
    if (reference) (*env)->DeleteGlobalRef(env, (jobject)(intptr_t)reference);
}
