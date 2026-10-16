# Aurora motion system

Shared Compose timings are defined by `VimivoMotion`:

| Role | Duration |
|---|---:|
| Immediate accessibility fallback | 0 ms |
| Press/favorite feedback | 120 ms |
| Small state transition | 160 ms |
| Navigation | 240 ms |
| Sheet | 300 ms |
| Player expansion | 360 ms |

These are defaults, not permission to animate every element. Each view should animate at most one or two hierarchy-defining changes. Playback commands respond immediately and never wait for decorative motion.

When `LocalReducedMotion` is true, decorative translation, scale, spring, and shared-element movement resolve directly to the final state. Functional buffering/progress feedback may remain when it communicates real state.

No frame-rate claim is made. Animation performance must be measured in a running native build before release verification.
