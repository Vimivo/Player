# libmpv CoreAudio shutdown crash (macOS)

## Observation

- **When and where:** a local smoke run (`VIMIVO_SMOKE`, forced software GL) on 2026-10-20 crashed the JVM once.
- **Log:** [`hs_err_pid60070.log`](hs_err_pid60070.log).
- **Faulting frame:**

  ```
  C  [libmpv.2.dylib]  mp_msg_va+0x28
  C  [libmpv.2.dylib]  mp_msg+0x1c
  C  [libmpv.2.dylib]  hotplug_cb+0x28
  C  [CoreAudio]       HALObject::PropertiesChanged
  C  [CoreAudio]       HALSystem::ObjectsPublishedAndDied
  ...                  libdispatch (CoreAudio's notification queue)
  ```

- **What the trace shows:** a CoreAudio "devices published/died" notification reached mpv's `ao_coreaudio`
  `hotplug_cb` after the `struct ao` it points to had been freed. The callback logs through `ao->log`, which is gone.

## Analysis (mpv 0.41.0 source)

- **Who registers a listener** (`AudioObjectAddPropertyListener` on the system object):
  - every open CoreAudio output (`init` → `register_hotplug_cb`);
  - the device-list watcher, which mpv creates when a client reads `audio-device-list`. Vimivo's output-device picker
    does. That watcher lives until `mpv_terminate_destroy`.
- **No in-flight guard:** `unregister_hotplug_cb` calls `AudioObjectRemovePropertyListener`. That call does not wait for
  notifications CoreAudio has already queued on its dispatch queue.
- **What triggers the notification:** closing an AudioUnit makes CoreAudio publish and kill per-process aggregate
  devices, and it reports that asynchronously.
- **Audio stays open until terminate:** with `gapless-audio=yes`, mpv keeps the audio output open into idle mode. It is
  then torn down inside `mpv_terminate_destroy`, together with the device-list watcher.
  - The notification about the closing AudioUnit can therefore arrive after both are freed.

## Mitigation in Vimivo (`MpvPlayerEngine.close()`)

1. Set `gapless-audio=no`, then `stop`. mpv closes the audio output, removing its listener, while the core is alive.
2. Wait until `idle-active` (at most 2 s).
3. On macOS, wait 150 ms so CoreAudio delivers the AudioUnit-teardown notifications while every listener owner
   still exists.
4. Detach the renderer, then call `mpv_terminate_destroy`.

## Reproduction attempts

`MpvShutdownStressTest` plays audio, reads the device list and closes the player, in a loop
(`VIMIVO_SHUTDOWN_STRESS=<n>`).

| Code | Cycles | Crashes |
|---|---|---|
| Before the mitigation | 150 (close while playing) + 400 (close right after load) | 0 |
| After the mitigation | 150 | 0 |

The crash did not reproduce, so no upstream issue has been filed yet.

## If it reproduces: upstream report draft

> **ao_coreaudio: use-after-free in hotplug_cb when a device notification is in flight at uninit**
>
> **Version:** mpv 0.41.0, libmpv, macOS 26, CoreAudio output.
>
> **Problem:**
> - `unregister_hotplug_cb()` removes the listener with `AudioObjectRemovePropertyListener()`. That call does not
>   synchronise with notifications CoreAudio has already dispatched.
> - Closing the AudioUnit itself causes `kAudioHardwarePropertyDevices` changes (aggregate devices dying).
> - Those changes can call `hotplug_cb()` after `struct ao` is freed (crash in `mp_msg` via `MP_VERBOSE(ao, …)`).
>
> **Suggested fix:** register with `AudioObjectAddPropertyListenerBlock` on a private serial queue. Remove it with
> `AudioObjectRemovePropertyListenerBlock`, then `dispatch_sync` a barrier on that queue before freeing, and
> unregister before disposing the AudioUnit.
>
> Attach: `hs_err` log and a minimal libmpv loop (create → play → read `audio-device-list` → terminate).
