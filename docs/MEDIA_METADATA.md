# Desktop media metadata

Desktop scanning now starts a bounded metadata pass after each successful root scan. Only indexed local files whose size or modification time changed are inspected. Two extractions run concurrently at most, and the work is cancelled with the desktop library scope.

The initial extractor uses the JVM's `AudioSystem` API. It records only fields reported by the active JVM provider: title where available, artist, album, genre, track/year properties, duration, bitrate, sample rate, channels, audio encoding, and container. A generated WAV fixture verifies sample rate, channels, duration, and WAVE container extraction.

Unsupported, corrupt, or unavailable files receive a persisted `FAILED` extraction status and error text. They are retried only after the source size or modification timestamp changes. No media file is modified, no file is loaded through the active playback engine, and no metadata is guessed.

This does not yet provide full MP3/FLAC/MP4/MKV tags, embedded artwork, video dimensions, frame rate, chapters, or thumbnails. Those require a separately validated decoder/parser implementation; the existing schema has fields reserved for them, but Vimivo does not claim support until such extraction is implemented and tested.
