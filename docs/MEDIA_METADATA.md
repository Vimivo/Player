# Desktop media metadata

Desktop scanning now starts a bounded metadata pass after each successful root scan. Only indexed local files whose size or modification time changed are inspected. Two extractions run concurrently at most, and the work is cancelled with the desktop library scope.

Tagged desktop audio uses Jaudiotagger 3.0.1 for MP3, FLAC, M4A/MP4 audio, Ogg, Opus, WAV, and AIFF containers supported by that parser. Vimivo reads title, artist, album, album artist, genre, track/disc numbers, year, duration, bitrate, sample rate, channel information, encoding, container, and embedded artwork when present. JVM `AudioSystem` remains the bounded fallback for other runtime-supported audio formats. Extraction version 2 causes unchanged records produced by the earlier limited extractor to be inspected once again.

Embedded artwork is decoded defensively, limited to a 20 MiB source image, scaled to a maximum 1024-pixel edge, encoded as JPEG, and stored under the platform cache directory using a SHA-256 identity derived from the stable media identity, size, and modification time. Writes use a temporary file and atomic replacement where supported. The cache is capped at 256 MiB and is non-authoritative; original media is never modified.

Unsupported, corrupt, or unavailable files receive a persisted `FAILED` extraction status and error text. They are retried only after the source size or modification timestamp changes. No media file is modified, no file is loaded through the active playback engine, and no metadata is guessed.

Automated coverage includes generated WAV extraction, cache scaling/reuse, corrupt artwork rejection, and SQLite artwork URI integration. Redistribution fixtures for tagged MP3/FLAC/M4A have not yet been added, so those parser paths are implemented but not fixture-verified in this repository. Video dimensions, frame rate, chapters, and real frame thumbnails remain unimplemented.
