# Vimivo Localization & Unicode Text Audit

This document audits string localization, Unicode compliance, regional script rendering, and Bengali text layout across all supported platforms.

---

## 1. Primary Language Support

- **English (Default)**: Canonical source language for UI labels, technical diagnostics, accessibility descriptions, and error recovery recommendations.
- **Bengali (বাংলা)**: Complete visual and phonetic typography verification.

---

## 2. Bengali Script & Complex Glyphs Verification

Bengali script requires correct rendering of complex ligatures (যুক্তাক্ষর), vowel signs (কার), and halant combinations:

| Category | Test String | Verified Rendering Behavior |
|---|---|---|
| **National Anthem & Song Titles** | `আমার সোনার বাংলা - রবীন্দ্রনাথ ঠাকুর` | Correctly renders matras, vowel signs (ো, া), and conjuncts (ন্দ). |
| **Complex Ligatures** | `বিজ্ঞান, প্রযুক্তি, ক্ষান্তি, সান্ত্বনা ও আনন্দ` | Correctly combines `জ্ঞ` (জ+ঞ), `ক্ষ` (ক+ষ), `ন্ত` (ন+ত), `ন্ত্ব` (ন+ত+ব). |
| **Mixed-Script Metadata** | `Vimivo 2026: শান্তিনিকেতন (Santiniketan) Edition [BD/IN]` | Seamless inline rendering of Latin-1 and Bengali glyphs without baseline drift. |
| **Emoji & Unicode Symbols** | `🎵 প্রতিধ্বনিত সুর · Echoes of Melody (HQ Audio) 🎧` | Musical note glyphs and emoji render cleanly alongside Indic scripts. |

---

## 3. Directional & Multi-Script Text Handling

- **RTL Metadata**: Encountered Arabic or Hebrew track titles render with bidirectional (BiDi) paragraph formatting without inverting playback progress or directional controls (Play/Pause, Next/Previous remain standard LTR).
- **Long Titles & Ellipses**:
  - Labels use `TextOverflow.Ellipsis` on single-line list rows to prevent layout distortion.
  - Full titles are always accessible to screen readers via `contentDescription` or accessible tooltips.
- **Numbers & Durations**:
  - Durations and technical metrics use Western Arabic numerals with monospace tabular numbers to prevent jitter during real-time position updates.
