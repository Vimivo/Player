#!/usr/bin/env python3
import subprocess
import os

MEDIA_DIR = os.path.abspath(os.path.dirname(__file__))

def run(cmd):
    print("Running:", " ".join(cmd))
    subprocess.check_call(cmd)

# 1. Generate artwork image
artwork_path = os.path.join(MEDIA_DIR, "artwork.png")
run([
    "ffmpeg", "-y", "-f", "lavfi",
    "-i", "color=c=0x00E5FF:s=500x500",
    "-frames:v", "1", "-update", "1",
    artwork_path
])

# 2. Track 1: FLAC with embedded artwork and lyrics
lyrics1 = "[00:00.00]Echoes in the dark\n[00:03.00]Drifting into solar winds\n[00:06.00]Every sound, every frame"
run([
    "ffmpeg", "-y",
    "-f", "lavfi", "-i", "sine=frequency=440:duration=10",
    "-i", artwork_path,
    "-map", "0:a", "-map", "1:v",
    "-c:a", "flac",
    "-c:v", "png",
    "-disposition:v", "attached_pic",
    "-metadata", "title=Solaris",
    "-metadata", "artist=Calyx",
    "-metadata", "album=Atmospheric Explorations",
    "-metadata", "genre=Ambient",
    "-metadata", "date=2026",
    "-metadata", f"lyrics={lyrics1}",
    "-metadata", "comment=Lossless Hi-Res Audio",
    os.path.join(MEDIA_DIR, "track1_solaris.flac")
])

# 3. Track 2: MP3 with embedded artwork and lyrics
lyrics2 = "[00:00.00]Over the crater ridge\n[00:04.00]Night turns to neon dawn\n[00:08.00]Endless orbit"
run([
    "ffmpeg", "-y",
    "-f", "lavfi", "-i", "sine=frequency=554:duration=10",
    "-i", artwork_path,
    "-map", "0:a", "-map", "1:v",
    "-c:a", "libmp3lame", "-b:a", "320k",
    "-c:v", "png",
    "-id3v2_version", "3",
    "-metadata", "title=Lunar Horizon",
    "-metadata", "artist=Calyx",
    "-metadata", "album=Atmospheric Explorations",
    "-metadata", "genre=Electronic",
    "-metadata", "date=2026",
    "-metadata", f"lyrics={lyrics2}",
    os.path.join(MEDIA_DIR, "track2_lunar.mp3")
])

# 4. Track 3: FLAC with embedded artwork and lyrics
lyrics3 = "[00:00.00]Waves of violet light\n[00:05.00]Cyan glow across the sky"
run([
    "ffmpeg", "-y",
    "-f", "lavfi", "-i", "sine=frequency=659:duration=10",
    "-i", artwork_path,
    "-map", "0:a", "-map", "1:v",
    "-c:a", "flac",
    "-c:v", "png",
    "-disposition:v", "attached_pic",
    "-metadata", "title=Aurora Borealis",
    "-metadata", "artist=Vimivo Ensemble",
    "-metadata", "album=Nordic Lights",
    "-metadata", "genre=Modern Classical",
    "-metadata", "date=2026",
    "-metadata", f"lyrics={lyrics3}",
    os.path.join(MEDIA_DIR, "track3_aurora.flac")
])

# 5. Video 1: Short MP4 Landscape 1920x1080 (H.264)
run([
    "ffmpeg", "-y",
    "-f", "lavfi", "-i", "testsrc=size=1920x1080:rate=30",
    "-f", "lavfi", "-i", "sine=frequency=1000:duration=5",
    "-t", "5",
    "-c:v", "libx264", "-pix_fmt", "yuv420p",
    "-c:a", "aac", "-b:a", "192k",
    "-metadata", "title=Landscape 1080p Test",
    os.path.join(MEDIA_DIR, "video_landscape.mp4")
])

# 6. Video 2: Short MP4 Portrait 1080x1920 (H.264 9:16)
run([
    "ffmpeg", "-y",
    "-f", "lavfi", "-i", "testsrc=size=1080x1920:rate=30",
    "-f", "lavfi", "-i", "sine=frequency=880:duration=5",
    "-t", "5",
    "-c:v", "libx264", "-pix_fmt", "yuv420p",
    "-c:a", "aac", "-b:a", "192k",
    "-metadata", "title=Portrait 9:16 Test",
    os.path.join(MEDIA_DIR, "video_portrait.mp4")
])

# 7. Subtitles for MKV
srt_path = os.path.join(MEDIA_DIR, "sample.srt")
with open(srt_path, "w") as f:
    f.write("1\n00:00:00,500 --> 00:00:02,500\nVimivo: Every Sound. Every Frame.\n\n2\n00:00:02,600 --> 00:00:04,800\nPrecision Local Media Playback\n")

# 8. Video 3: MKV with video, audio, and embedded subtitle track
run([
    "ffmpeg", "-y",
    "-f", "lavfi", "-i", "testsrc=size=1280x720:rate=30",
    "-f", "lavfi", "-i", "sine=frequency=523:duration=5",
    "-i", srt_path,
    "-t", "5",
    "-c:v", "libx264", "-pix_fmt", "yuv420p",
    "-c:a", "aac", "-b:a", "192k",
    "-c:s", "srt",
    "-metadata", "title=Matroska Multi-Track Test",
    "-metadata:s:s:0", "language=eng",
    os.path.join(MEDIA_DIR, "video_sample.mkv")
])

# 9. Public HLS stream URLs
with open(os.path.join(MEDIA_DIR, "stream_urls.txt"), "w") as f:
    f.write("https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8\n")
    f.write("https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8\n")

print("\nSuccessfully generated all test media in", MEDIA_DIR)
