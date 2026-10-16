# Vimivo User-Driven Product Roadmap

## 1. Roadmap Principles & Structure

Vimivo maintains a transparent **NOW / NEXT / LATER** roadmap. 
- **NOW**: Actively scheduled or currently in progress. Backed by high-confidence evidence and automated test requirements.
- **NEXT**: Planned for upcoming release cycles pending stabilization of current work.
- **LATER**: Validated improvements under evaluation, awaiting capacity or further technical investigation.
- **NEEDS RESEARCH**: Complex proposals requiring format probing, native library evaluation, or architecture spikes.
- **DEFERRED**: Requests that conflict with offline-first principles, require cloud account logins, or introduce telemetry.

---

## 2. Active Roadmap Backlog

### NOW (Release Candidate Stabilization & Community Feedback)
- [x] **Privacy-Safe In-App Feedback Infrastructure**: Structured composers for Bug Reports, Feature Requests, and Playback Issues (Shared, Desktop, Android).
- [x] **Offline Draft Persistence**: Local file-based draft storage and export without touching SQLite schema.
- [x] **Diagnostic Sanitization (`SafeLogger`)**: Automatic redaction of credentials, auth tokens, and filesystem paths.
- [x] **Verified GitHub Issue Templates**: GitHub forms for bugs, playback, feature requests, accessibility, and packaging.
- [x] **Automated Regression Quality Gate**: Full verification suite passing 300+ unit, integration, and UI tests.

### NEXT (Post-1.0.0 General Availability Polish)
- [ ] **Multi-Format Subtitle Styling Preferences**: Custom font family, border thickness, and shadow blur settings for ASS/SSA subtitle renderers.
- [ ] **ReplayGain & Volume Normalization**: Per-track and per-album ReplayGain 2.0 metadata support in desktop audio pipeline.
- [ ] **AB Loop Playback**: Repeat specific time intervals within a song or video for rehearsal, language learning, or study.
- [ ] **Media Chapter Visualization**: Visual chapter markers and title callouts along the playback seek bar.

### LATER (Future Architecture & Extended Features)
- [ ] **Optical Media / Audio CD Playback**: Direct playback from local optical drives on supported desktop platforms.
- [ ] **Advanced Audio DSP Plugins**: Parametric equalizer with arbitrary Q-factor and LADSPA/AU plugin hosting on desktop.
- [ ] **Folder Watcher Auto-Rescan**: Inotify / FSEvents background directory monitoring for instant library updates.

### DEFERRED / REJECTED (Contradicts Product Principles)
- [x] **Mandatory Cloud Accounts / Synchronization**: Rejected. Vimivo is login-free and privacy-first.
- [x] **Telemetry & Usage Analytics**: Rejected. Zero tracking, zero telemetry.
- [x] **In-App Advertisements or Sponsored Content**: Rejected. 100% ad-free forever.
- [x] **Proprietary DRM Decryption**: Rejected. Local media and authorized sources only; no bypassing of access controls.
