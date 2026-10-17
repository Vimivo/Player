# Apple Dynamic Island & Live Activities Architecture (Milestone 65 & 66)

Vimivo delivers a native iOS Live Activity and Dynamic Island experience using Apple's **ActivityKit** and **WidgetKit** frameworks, synchronized with the authoritative media playback engine.

---

## 1. Architectural Foundation

In compliance with the Vimivo product boundaries:
- **Zero Cloud / Account Dependency**: All metadata originates from the local media item or the active direct URL stream.
- **Authoritative Single Source of Truth**: The Live Activity does not create an independent playback engine or secondary queue. It mirrors the exact playback state published by `AVPlayerPlaybackEngine` through `NowPlayingManager` and `WidgetPlaybackSnapshotStore`.
- **Target OS Compatibility**: Guarded by `#if canImport(ActivityKit)` and `@available(iOS 16.1, *)`. The host app deployment target remains iOS 16.0+, while the widget extension targets iOS 17.0+.

---

## 2. Activity Attributes & Content State

Defined in `iosApp/iosApp/Playback/WidgetPlaybackIntents.swift` (compiled into both `iosApp` and `VimivoWidgets` extension targets):

```swift
#if canImport(ActivityKit)
import ActivityKit

@available(iOS 16.1, *)
public struct VimivoPlaybackActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public var title: String
        public var artist: String
        public var isPlaying: Bool
        public var positionSeconds: Double
        public var durationSeconds: Double
        public var updatedAt: Date

        public init(
            title: String,
            artist: String,
            isPlaying: Bool,
            positionSeconds: Double,
            durationSeconds: Double,
            updatedAt: Date = Date()
        ) {
            self.title = title
            self.artist = artist
            self.isPlaying = isPlaying
            self.positionSeconds = positionSeconds
            self.durationSeconds = durationSeconds
            self.updatedAt = updatedAt
        }
    }

    public var mediaID: String

    public init(mediaID: String) {
        self.mediaID = mediaID
    }
}
#endif
```

---

## 3. Dynamic Island Presentation Modes

Implemented in `iosApp/VimivoWidgets/VimivoWidgets.swift`:

1. **Compact Leading**:
   - Distinctive purple music note / video glyph identifying Vimivo.
2. **Compact Trailing**:
   - Live visual indicator (`waveform` when active playback is running, `pause.fill` when paused).
3. **Minimal**:
   - Cohesive single-element badge when multiple Live Activities share the Dynamic Island.
4. **Expanded** (Long-press / Expand):
   - **Leading Region**: Media icon, high-contrast title (semibold headline), and artist name (secondary caption).
   - **Trailing Region**: Active playback waveform / state glyph.
   - **Bottom Region**: Continuous progress bar tracking elapsed / remaining duration, alongside interactive controls:
     - Previous Track (`PreviousWidgetTrackIntent`)
     - Play / Pause Toggle (`ToggleWidgetPlaybackIntent`)
     - Next Track (`NextWidgetTrackIntent`)
   - **Interaction**: Tap gesture dispatches deep link `vimivo://now-playing` to resume the full-screen player.

---

## 4. Lock Screen & StandBy Mode Banner

For devices locked, on charging stands (StandBy), or without a Dynamic Island:
- Elevated glass card styled in accordance with **Vimivo Aurora Glass** tokens.
- Displays media artwork glyph, track title, artist / album line, progress bar, and interactive transport buttons.
- Fully accessible with system text scaling and high contrast.

---

## 5. Lifecycle & Resource Management

Coordinated in `iosApp/iosApp/Playback/WidgetPlaybackSnapshotStore.swift`:
- **Startup**: On track load, requests an `Activity<VimivoPlaybackActivityAttributes>` if `ActivityAuthorizationInfo().areActivitiesEnabled` is true. Re-attaches to existing sessions if app was backgrounded.
- **Updates**: State transitions (Play / Pause / Track Skip) update the activity immediately. Continuous progress ticks update at 15-second intervals to strictly respect Apple system power budgets and avoid watchdog throttling.
- **Teardown**: When playback stops or `clear()` is invoked, all active activities are ended immediately with `dismissalPolicy: .immediate`.

---

## 6. Verification Status

| Target / Suite | Environment | Status | Evidence |
|---|---|---|---|
| `iosApp` compilation | Xcode 27.1 / macOS | **VERIFIED** | `** BUILD SUCCEEDED **` with `NSSupportsLiveActivities` in Info.plist |
| `VimivoWidgets.appex` | Xcode 27.1 / macOS | **VERIFIED** | Live Activity & WidgetBundle compiled into app extension |
| `MediaModelsTests` | iOS Simulator 27.1 (`iPhone 17 Pro`) | **VERIFIED** | `testPlaybackActivityAttributesRoundTrip` passed in 0.001s |
| Physical Hardware | iPhone 14 Pro / 15 / 16 / 17 | Pending | Requires Apple Developer Program team signing profile |
