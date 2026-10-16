# Embedded Tag Writing Capabilities

This document records the exact format capabilities and limitations of Vimivo's native embedded tag editing pipeline.

---

## 1. Supported Format Matrix

| Container / Format | Tag Standard | Embedded Tag Write | Embedded Artwork Write | Notes / Backend |
|---|---|:---:|:---:|---|
| **MP3** (`.mp3`) | ID3v2.3 / ID3v2.4 | **Supported** | **Supported** | Fully supported via `AudioFileIO`. ID3v1/v2 preserved. |
| **FLAC** (`.flac`) | Vorbis Comments | **Supported** | **Supported** | Picture block replacement verified. Lossless stream untouched. |
| **Ogg Vorbis** (`.ogg`) | Vorbis Comments | **Supported** | **Supported** | Vorbis comment blocks written atomically. |
| **M4A / AAC** (`.m4a`) | MP4 / iTunes Atoms | **Supported** | **Supported** | `covr`, `©nam`, `©ART`, `©alb` atoms updated. |
| **WAV** (`.wav`) | RIFF INFO / ID3 | **Supported** | **Unsupported** | Text tags supported where RIFF/ID3 chunk exists. Artwork routed to override. |
| **Opus** (`.opus`) | Ogg Opus | **Unsupported** | **Unsupported** | Routed to **Database Override (Mode A)** to prevent container corruption. |
| **MKV / WebM** (`.mkv`, `.webm`) | Matroska EBML | **Unsupported** | **Unsupported** | Video containers are strictly read-only for tag writing. Routed to Mode A. |
| **MP4 Video** (`.mp4`) | ISO BMFF | **Unsupported** | **Unsupported** | Video files default to Mode A to protect video streams. |

---

## 2. Capability Preflight Validation

Before executing any file write, `DesktopMetadataEditorService` executes `isFormatSupportedForEmbeddedWrite`:

```kotlin
fun isFormatSupportedForEmbeddedWrite(file: File): Boolean {
    val ext = file.extension.lowercase()
    return ext in WRITABLE_AUDIO_EXTENSIONS // mp3, flac, ogg, m4a, wav
}
```

If `isSupportedFormat` is `false`:
- The UI flags the item: `"Format not supported for embedded writing; use override"`.
- Mode A (Database Override) is suggested or automatically applied.
- The original file bytes remain untouched.

---

## 3. Artwork Validation

When embedding artwork:
- Formats accepted: PNG, JPEG.
- Validation checks: File readability, valid image header, reasonable dimensions (< 4096x4096px), reasonable file size (< 8 MB).
- Replaces existing embedded artwork field (`tag.deleteArtworkField() -> tag.setField(artwork)`).
- Re-reads and verifies tag after commit (`require(verified.tag?.firstArtwork != null)`).
