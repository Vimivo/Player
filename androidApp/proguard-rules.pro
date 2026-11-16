# JNI entry points of Vimivo's libmpv bridge (src/main/cpp/vimivo_mpv_jni.c) are bound by class and method name.
-keep class dev.vimivo.platform.android.playback.LibmpvNative { native <methods>; }

# Optional desktop/Java-only dependencies transitively included by SMB / audio tagging libraries
-dontwarn javax.el.**
-dontwarn org.ietf.jgss.**
