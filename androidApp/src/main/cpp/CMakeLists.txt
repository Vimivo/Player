# Vimivo's JNI bridge to the LGPL libmpv (ADR-005). libmpv, FFmpeg and their dependencies come prebuilt from the
# pinned media package (scripts/build_media, media.lock); VIMIVO_ANDROID_MEDIA is its unpacked root.
cmake_minimum_required(VERSION 3.22)
project(vimivo_mpv C)

set(MEDIA "${VIMIVO_ANDROID_MEDIA}")
set(LIBS "${MEDIA}/jniLibs/${ANDROID_ABI}")

add_library(mpv SHARED IMPORTED)
set_target_properties(mpv PROPERTIES IMPORTED_LOCATION "${LIBS}/libmpv.so")
add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION "${LIBS}/libavcodec.so")

add_library(vimivo_mpv SHARED vimivo_mpv_jni.c)
target_include_directories(vimivo_mpv PRIVATE "${MEDIA}/include")
target_compile_options(vimivo_mpv PRIVATE -Wall -Wextra -Werror)
target_link_libraries(vimivo_mpv mpv avcodec)
# Android 15+ devices may use 16 KB pages.
target_link_options(vimivo_mpv PRIVATE "-Wl,-z,max-page-size=16384")
