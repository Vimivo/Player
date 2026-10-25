# Media test matrix

Only legal, self-created, Creative Commons, or generated media may be added to test fixtures. Large/binary media should live in an external test corpus or Git LFS, not ordinary Git history. Record the device, Android version, SoC/decoder, source provenance, checksum, engine, and result for every manual run.

| Case | Suggested legal fixture | Expected primary behavior | Fallback expectation | Automated now |
|---|---|---|---|---|
| Standard MP4 | Generated color bars + sine audio, H.264/AAC | Media3 opens, renders, seeks, completes | Only on decoder failure | No; device corpus required |
| MKV, two audio tracks | Generated tracks with spoken labels | Media3 reports/selects both tracks | libmpv on verified parse/decode failure | No |
| HEVC video | Generated HEVC test pattern | Media3 on capable device; honest decoder error otherwise | libmpv once | No |
| FLAC audio | Generated sine sweep | Media3 plays and seeks | libmpv on decode failure | No |
| Corrupted/unsupported | Truncated generated file | Meaningful parsing/decoding error | At most one libmpv attempt | Manager policy unit-tested |
| Missing file | Deleted temporary fixture URI | Source-not-found error | No fallback | Manager policy unit-tested |
| Playback interruption | Audio-focus test app/call simulation | Pause on focus loss | No engine switch | Manual/device |
| Repeated open/close | Generated short MP4, 100 cycles | No simultaneous players/native leaks | N/A | Manager cleanup unit-tested partially; stress test pending |
| Engine switching | Media3 decoder-failure fixture | Prior engine released, queue/position retained | Switch once to libmpv | Unit-tested |
| HTTP/HTTPS | Local authorized test server | Media3 streams and buffers | Decoder failures only; not HTTP/auth failures | Manual/instrumentation pending |
| 16 KB page size | Same MP4 on official 16 KB system image | Both engines load native libraries and play | N/A | APK alignment verified; runtime pending |
| Multiple audio/subtitle tracks | Generated MKV | Menu lists only reported tracks and selection remains service-owned | libmpv supports equivalent selection | Instrumentation/device run pending |
| Seek gestures | Generated two-minute MP4 | Double tap and horizontal drag clamp to valid duration | Same session commands | Resume policy and query logic unit-tested; touch run pending |
| PiP and orientation | Generated landscape MP4 | Playback continues with synchronized notification/session state | Same | Physical API 26 and API 31+ run pending |
| Hardware decoding | Generated H.264 and HEVC variants | Uses platform decoder when available; reports errors honestly | libmpv fallback only for verified decoder failures | Device-specific run pending |
| Interrupted video | Generated MP4 | Position persists; final ten seconds restart from zero | Same | Resume policy unit-tested; audio-focus device run pending |
| External SRT/WebVTT | Generated MP4 plus UTF-8 sidecar | SAF load, persisted grant, selection, and readable cues | Native slave load | File matching/format tests added; device rendering pending |
| Styled ASS/SSA | Generated MKV and sidecar with positioning/styles | Supported cue styling only; full fidelity is not claimed | Native renderer should retain supported complex styles | Visual device comparison pending |
| Subtitle/audio delay | Generated A/V sync fixture | Controls correctly reported unavailable | ±10 second adjustment in 500 ms steps | libmpv device verification pending |
| Revoked subtitle permission | Previously selected SAF subtitle | Actionable load error; video remains playable | Same | Provider/device test pending |

## Generated fixture example

If FFmpeg is installed locally, a developer may generate a legally clean sample without committing it:

```shell
ffmpeg -f lavfi -i testsrc2=size=1280x720:rate=30 \
  -f lavfi -i sine=frequency=440:sample_rate=48000 \
  -t 10 -c:v libx264 -c:a aac /tmp/vimivo-standard.mp4
```

Checksums and exact generation commands should accompany any fixture admitted to the maintained corpus.

