# Vimivo — Physical Hardware & Peripheral Manual Test Checklist

This checklist covers multimedia features that require physical hardware devices, external peripheral connections, or dedicated audio-visual receivers that cannot be completely automated in virtualized or headless CI runners.

---

## 1. High-Fidelity Audio Bitstream Passthrough (HDMI / S/PDIF)

### Prerequisites
- AV Receiver (AVR) or Soundbar connected via HDMI eARC or Optical S/PDIF.
- Test clips: `testmedia/pack/ac3.ac3`, `testmedia/pack/dts.dts`, and TrueHD/Atmos sample files.

### Steps
1. In Vimivo Desktop, navigate to **Audio -> Audio Passthrough (S/PDIF / HDMI)** or press `Cmd+K` / `Ctrl+K` and search `Passthrough`.
2. Select **Enable Bitstream Passthrough (AC3, DTS, E-AC3, TrueHD, DTS-HD)**.
3. Open an AC-3 or DTS multi-channel media file.
4. Verify the front panel of the physical AV Receiver lights up with the format identifier (`DOLBY DIGITAL`, `DTS SURROUND`, `TRUEHD`).
5. Verify software volume adjustments in the OS are bypassed and volume is controlled exclusively via the hardware receiver.

---

## 2. Hardware GPU Acceleration & Power Consumption

### Prerequisites
- Target machines with distinct GPU chipsets:
  - Apple Silicon (M1/M2/M3/M4 VideoToolbox)
  - Intel Core (QuickSync / VA-API / QSV)
  - AMD Radeon (RADV / VA-API / AMF)
  - NVIDIA GeForce (NVDEC / D3D11VA / CUDA)
- High-bitrate 8K 60fps HEVC 10-bit and AV1 clips (`testmedia/pack/av1.mkv`, `testmedia/pack/hdr10_chapters_ass.mkv`).

### Steps
1. Open an 8K 60fps AV1 or HEVC 10-bit file.
2. Press `Shift+I` (or **Tools -> Technical Stats / Stats for Nerds**).
3. Confirm **Hardware Decoding** displays `videotoolbox`, `vaapi`, `d3d11va`, or `nvdec` (not `software` or `sw`).
4. Monitor CPU utilization in Activity Monitor (macOS), Task Manager (Windows), or `htop` (Linux): verify CPU load remains under 8% with 0 dropped frames over 60 seconds of playback.

---

## 3. Bluetooth Audio Latency & AVRCP Hardware Buttons

### Prerequisites
- Physical Bluetooth headphones / earbuds (AirPods, Sony WH-1000XM, Bose QC) paired to the test device.

### Steps
1. Play video clip with dialogue and sharp transients (`testmedia/video_landscape.mp4`).
2. Verify lip-sync matches video frames without manual audio delay adjustment.
3. Press physical hardware buttons on the headset:
   - Single tap: Video pauses immediately; second tap resumes.
   - Double tap: Skips to the next track in the queue.
   - Triple tap: Jumps to the previous track.
4. Disconnect Bluetooth headphones mid-playback:
   - Verify playback pauses immediately (anti-embarrassment auto-pause on audio route disconnect).

---

## 4. Google Cast / Chromecast Receiver Verification

### Prerequisites
- Physical Chromecast with Google TV or smart TV connected to the same local Wi-Fi network.

### Steps
1. In Vimivo Desktop or Mobile, open the **Casting** menu (**Tools -> Cast to Device**).
2. Verify the local Chromecast receiver is discovered via mDNS/SSDP within 3 seconds.
3. Select the cast target:
   - Verify the TV screen displays the Vimivo playback overlay and begins streaming.
   - Adjust volume and seek bar on the host device: verify target playback updates within 200 ms.
   - Disconnect cast session: verify playback resumes smoothly on the local screen.

---

## 5. Apple AirPlay 2 Target Verification

### Prerequisites
- Physical Apple TV 4K or AirPlay 2 compatible speaker (HomePod) on the local network.
- macOS or iOS host running Vimivo.

### Steps
1. On macOS or iOS, select the AirPlay output icon in the player or Control Center.
2. Select the physical Apple TV or HomePod.
3. Verify video stream renders on the Apple TV with multi-channel audio routed to the surround setup.
4. Verify playback metadata (title, artist, album art) is reflected on the Apple TV screen.

---

## 6. Android Auto & Apple CarPlay Dashboard

### Prerequisites
- Vehicle head unit supporting wired or wireless Android Auto / Apple CarPlay.

### Steps
1. Connect mobile device running Vimivo to the vehicle head unit.
2. Select Vimivo from the dashboard media apps list.
3. Verify the Now Playing screen displays title, artist, progress bar, and large touch-friendly buttons.
4. Test steering wheel controls (Skip, Previous, Play/Pause): verify immediate response.
5. Trigger incoming navigation prompt or phone call: verify media ducks or pauses cleanly and resumes once the call ends.

---

## 7. Multi-Monitor Mixed-DPI Window Dragging

### Prerequisites
- Desktop host with at least two physical monitors configured with different DPI scaling (e.g. 200% Retina internal display + 100% 1080p external monitor, or 150% 4K on Windows).

### Steps
1. Launch Vimivo Desktop and start playing a high-resolution video.
2. Drag the player window across the monitor boundary from the 200% Retina screen to the 100% external screen.
3. Verify:
   - Zero visual flickering or surface clipping during the transition.
   - Subtitle text, control bar icons, and OSD fonts automatically adapt to the target monitor's scale factor.
   - Playback framerate remains smooth without stutter or frame drops during dragging.

---

## 8. Physical Optical Disc Drive Insertion (DVD-Video & Blu-ray)

### Prerequisites
- External USB or internal SATA optical disc drive.
- Unencrypted physical DVD-Video and Blu-ray discs.

### Steps
1. Insert physical disc into the drive.
2. Launch Vimivo Desktop and open **File -> Open Disc -> Open Blu-ray / DVD Drive** (or pass `/Volumes/<DISC_NAME>` or `D:\` via CLI).
3. Verify:
   - Disc structure is identified automatically via `DesktopDiscMedia`.
   - Primary feature stream (`STREAM/00001.m2ts` or `VIDEO_TS/VTS_01_1.VOB`) begins playing.
   - Chapters and embedded audio/subtitle streams are listed and selectable.
