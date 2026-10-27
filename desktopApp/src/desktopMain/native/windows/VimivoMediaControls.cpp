// Windows System Media Transport Controls (media flyout, lock screen, hardware media keys) for Vimivo.
//
// Unpackaged desktop apps get SMTC through a Windows.Media.Playback.MediaPlayer whose built-in command
// manager is disabled, so Vimivo drives the controls manually while libmpv keeps doing the playback.
// Built with C++/WinRT so every WinRT call is type-checked against the Windows SDK.

#include <jni.h>

#include <chrono>
#include <mutex>
#include <string>

#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Media.Playback.h>

using namespace winrt;
using namespace winrt::Windows::Media;
using namespace winrt::Windows::Media::Playback;

namespace {
std::mutex stateLock;
JavaVM *javaVm = nullptr;
jobject callback = nullptr;
MediaPlayer mediaPlayer{nullptr};
SystemMediaTransportControls controls{nullptr};
event_token buttonToken{};
event_token seekToken{};

void callJava(const char *method, const char *signature, jlong value = 0) {
    JavaVM *vm = nullptr;
    jobject target = nullptr;
    {
        std::lock_guard<std::mutex> guard(stateLock);
        vm = javaVm;
        target = callback;
    }
    if (!vm || !target) return;
    JNIEnv *env = nullptr;
    // WinRT raises events on thread-pool threads; attaching as a daemon never blocks JVM shutdown.
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK &&
        vm->AttachCurrentThreadAsDaemon(reinterpret_cast<void **>(&env), nullptr) != JNI_OK) {
        return;
    }
    jclass type = env->GetObjectClass(target);
    jmethodID id = env->GetMethodID(type, method, signature);
    if (id) {
        if (signature[1] == 'J') env->CallVoidMethod(target, id, value);
        else env->CallVoidMethod(target, id);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(type);
}

hstring toHString(JNIEnv *env, jstring value) {
    if (!value) return hstring{};
    const jchar *chars = env->GetStringChars(value, nullptr);
    const jsize length = env->GetStringLength(value);
    hstring result{reinterpret_cast<const wchar_t *>(chars), static_cast<uint32_t>(length)};
    env->ReleaseStringChars(value, chars);
    return result;
}

void onButton(SystemMediaTransportControls const &, SystemMediaTransportControlsButtonPressedEventArgs const &args) {
    switch (args.Button()) {
    case SystemMediaTransportControlsButton::Play: callJava("remotePlay", "()V"); break;
    case SystemMediaTransportControlsButton::Pause: callJava("remotePause", "()V"); break;
    case SystemMediaTransportControlsButton::Next: callJava("remoteNext", "()V"); break;
    case SystemMediaTransportControlsButton::Previous: callJava("remotePrevious", "()V"); break;
    case SystemMediaTransportControlsButton::Stop: callJava("remotePause", "()V"); break;
    default: break;
    }
}

void onSeek(SystemMediaTransportControls const &, PlaybackPositionChangeRequestedEventArgs const &args) {
    const auto millis = std::chrono::duration_cast<std::chrono::milliseconds>(args.RequestedPlaybackPosition());
    callJava("remoteSeek", "(J)V", static_cast<jlong>(millis.count()));
}
} // namespace

extern "C" JNIEXPORT jint JNICALL
Java_dev_vimivo_desktop_integration_windows_WindowsMediaControlsService_configureNative(JNIEnv *env, jobject self) {
    try {
        try {
            init_apartment(apartment_type::multi_threaded);
        } catch (hresult_error const &) {
            // The calling thread already joined a COM apartment; WinRT objects used here are agile.
        }
        std::lock_guard<std::mutex> guard(stateLock);
        if (controls) return 0;
        if (env->GetJavaVM(&javaVm) != JNI_OK) return -1;
        callback = env->NewGlobalRef(self);
        mediaPlayer = MediaPlayer();
        mediaPlayer.CommandManager().IsEnabled(false);
        controls = mediaPlayer.SystemMediaTransportControls();
        controls.IsPlayEnabled(true);
        controls.IsPauseEnabled(true);
        controls.IsStopEnabled(true);
        controls.IsNextEnabled(true);
        controls.IsPreviousEnabled(true);
        controls.IsEnabled(false);
        buttonToken = controls.ButtonPressed(
            [](SystemMediaTransportControls const &sender, SystemMediaTransportControlsButtonPressedEventArgs const &args) {
                onButton(sender, args);
            });
        seekToken = controls.PlaybackPositionChangeRequested(
            [](SystemMediaTransportControls const &sender, PlaybackPositionChangeRequestedEventArgs const &args) {
                onSeek(sender, args);
            });
        return 0;
    } catch (hresult_error const &error) {
        return static_cast<jint>(error.code());
    } catch (...) {
        return -2;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vimivo_desktop_integration_windows_WindowsMediaControlsService_updateNative(
    JNIEnv *env, jobject, jstring title, jstring artist, jstring album, jlong durationMillis,
    jlong positionMillis, jboolean playing, jboolean video, jboolean hasNext, jboolean hasPrevious) {
    try {
        std::lock_guard<std::mutex> guard(stateLock);
        if (!controls) return;
        auto updater = controls.DisplayUpdater();
        if (!title) {
            updater.ClearAll();
            updater.Update();
            controls.PlaybackStatus(MediaPlaybackStatus::Stopped);
            controls.IsEnabled(false);
            return;
        }
        updater.Type(video ? MediaPlaybackType::Video : MediaPlaybackType::Music);
        if (video) {
            updater.VideoProperties().Title(toHString(env, title));
            updater.VideoProperties().Subtitle(toHString(env, artist));
        } else {
            updater.MusicProperties().Title(toHString(env, title));
            updater.MusicProperties().Artist(toHString(env, artist));
            updater.MusicProperties().AlbumTitle(toHString(env, album));
        }
        updater.Update();

        using std::chrono::milliseconds;
        SystemMediaTransportControlsTimelineProperties timeline;
        const auto end = milliseconds(durationMillis > 0 ? durationMillis : 0);
        timeline.StartTime(milliseconds(0));
        timeline.MinSeekTime(milliseconds(0));
        timeline.EndTime(end);
        timeline.MaxSeekTime(end);
        timeline.Position(milliseconds(positionMillis > 0 ? positionMillis : 0));
        controls.UpdateTimelineProperties(timeline);

        controls.IsNextEnabled(hasNext == JNI_TRUE);
        controls.IsPreviousEnabled(hasPrevious == JNI_TRUE);
        controls.PlaybackStatus(playing ? MediaPlaybackStatus::Playing : MediaPlaybackStatus::Paused);
        controls.IsEnabled(true);
    } catch (...) {
        // A failed metadata update must never take playback down; the next update retries.
    }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_vimivo_desktop_integration_windows_WindowsMediaControlsService_closeNative(JNIEnv *env, jobject) {
    std::lock_guard<std::mutex> guard(stateLock);
    try {
        if (controls) {
            controls.ButtonPressed(buttonToken);
            controls.PlaybackPositionChangeRequested(seekToken);
            controls.IsEnabled(false);
        }
        if (mediaPlayer) mediaPlayer.Close();
    } catch (...) {
    }
    controls = nullptr;
    mediaPlayer = nullptr;
    if (callback) env->DeleteGlobalRef(callback);
    callback = nullptr;
    javaVm = nullptr;
}
