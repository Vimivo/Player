# Google Play listing draft

This is a draft, not a claim that every device supports every codec. Validate it against the release build before submission.

## Listing metadata

**App title (28/30 characters):** Vimivo: Music & Video Player

**Short description:** Private offline music and video playback with playlists, subtitles and streams.

**Full description:**

Vimivo is a privacy-first music and video player for media you control. Browse audio and video on your Android device, continue where you stopped, organize favorites and playlists, and play authorized network streams—all without an account or advertising.

Enjoy background music playback with system media controls, a focused fullscreen video experience, configurable gestures, playback speed, picture-in-picture, queues, bookmarks, search, and local backup and restore. Select embedded or external subtitle and audio tracks when the active playback engine and media provide them.

Vimivo uses Android's media permissions only for the library categories you choose. You can still open individual files and folders with Android's system picker without granting broad library access. Your library database, history, settings, playlists, and bookmarks stay on your device. Network stream URLs are sent only to the host you choose for playback.

Format and codec support depends on the Android device, media composition, and selected playback engine. DRM bypass, streaming-service scraping, cloud accounts, analytics, and ads are not included.

## Feature summary

- Local music and video library using Android MediaStore and system pickers
- Background audio, notification, lock-screen, Bluetooth, and headset controls
- Music queues, playlists, favorites, history, bookmarks, search, and resume positions
- Fullscreen video gestures, speed, aspect ratio, PiP, subtitles, and audio tracks
- Direct HTTP/HTTPS files, internet radio, HLS, and MPEG-DASH when supported
- Light, dark, system, and AMOLED themes with accessibility preferences
- Versioned local backup and restore; no media files included
- No ads, account, analytics SDK, or cloud dependency

## Assets

- App icon: upload a 512 × 512, 32-bit PNG derived from `docs/brand/vimivo-icon-source.png`; keep the full square and do not add Play-style rounded corners.
- Feature graphic: 1024 × 500 JPEG or 24-bit PNG, without alpha. Use the icon palette, original media artwork, minimal copy, and no device claims.
- Phone screenshots: capture at least Home, Music, Now Playing, Video Library, Video Player, Network Stream, and Settings in a supported aspect ratio.
- Tablet screenshots: capture adaptive navigation and representative library/player layouts on 7-inch and 10-inch profiles.
- Screenshots must show real app behavior with legal/generated media, readable UI, no debug banners, no personal URLs, and no unsupported codec claims.

Google's current asset guidance is maintained in [Preview assets](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en). Recheck dimensions and quantity in Play Console at submission time.

## Data Safety draft

- Data collected by developer: **No**, based on the current code and dependency set.
- Data shared with third parties: **No**. User-selected stream requests necessarily go to the destination host; Vimivo has no intermediary service.
- Data encrypted in transit: HTTPS streams are encrypted; explicitly entered HTTP/LAN streams are not. Do not answer this item without considering Google's current form wording.
- Account creation: none.
- Deletion request mechanism: not applicable to a developer backend. Users can clear history, streams, artwork cache, settings, or all local app data on-device.
- Ads: none. Analytics/crash-reporting SDK: none.
- Privacy policy: publish `docs/PRIVACY.md` at a stable public HTTPS URL.

Re-audit the final AAB and Play SDK Index before submitting; Play's form answers describe the shipped binary, not intentions.

## Permissions declaration summary

- `READ_MEDIA_AUDIO`: opt-in discovery of shared audio on Android 13+.
- `READ_MEDIA_VIDEO`: opt-in discovery of shared video on Android 13+.
- `READ_MEDIA_VISUAL_USER_SELECTED`: supports Android's selected-media behavior where applicable.
- `READ_EXTERNAL_STORAGE` (maximum API 32): legacy shared-media discovery.
- `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`: user-visible background playback owned by one MediaLibraryService.
- `INTERNET` and `ACCESS_NETWORK_STATE`: only user-requested network playback and connectivity-aware recovery.

`MANAGE_EXTERNAL_STORAGE`, location, contacts, microphone, camera, advertising ID, and notification permission are not requested. MediaStyle playback notification behavior is exempt from the Android 13 runtime notification permission; revalidate if non-media notifications are later added.

