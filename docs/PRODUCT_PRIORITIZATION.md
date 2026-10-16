# Vimivo Evidence-Based Product Prioritization Framework

## 1. Core Principles of Prioritization

Vimivo uses an evidence-based, transparent prioritization framework to determine what work gets scheduled. We do not use fake popularity polls, fabricated demand numbers, or vanity metrics.

### Guiding Principles:
1. **Security & Data Safety Over Everything**: P0 security vulnerabilities, credential leaks, and data corruption bugs take precedence over all feature requests regardless of popularity.
2. **Reproducibility Over Speculation**: Confirmed defects with reliable reproduction steps are scheduled ahead of vague or ambiguous reports.
3. **Core Playback Reliability First**: A media player's primary duty is flawless, low-latency playback with rock-solid audio/video synchronization.
4. **Offline-First & Privacy Preservation**: Features that compromise local privacy, require account logins, or introduce unnecessary remote dependencies are rejected or deprioritized.

---

## 2. Weighted Prioritization Score (WPS)

For feature requests and non-critical enhancements, Vimivo calculates a transparent Weighted Prioritization Score:

$$\text{Score} = \frac{(\text{Impact} \times 3) + (\text{Reach} \times 2) + (\text{EvidenceConfidence} \times 2) + (\text{A11yImpact} \times 2)}{(\text{Effort} \times 2) + (\text{MaintenanceRisk} \times 2)}$$

### Scoring Factors (1 to 5 scale):

- **User Impact (1-5)**:
  - 5: Resolves a complete blocker in a major workflow (e.g. playing MKV files with embedded subtitles).
  - 1: Minor visual convenience or cosmetic preference.
- **Platform Reach (1-5)**:
  - 5: Cross-platform (benefits Android, macOS, Windows, Linux).
  - 1: Highly niche platform-specific edge case.
- **Evidence Confidence (1-5)**:
  - 5: Confirmed reproduction with verified technical sample fixtures.
  - 1: Single speculative report without technical context.
- **Accessibility Impact (1-5)**:
  - 5: Removes an active barrier preventing screen-reader or keyboard navigation.
  - 1: No impact on assistive technology users.
- **Implementation Effort (1-5)**:
  - 1: Minimal changes, well-isolated shared code.
  - 5: Architectural overhaul, native engine rework, complex database migration.
- **Maintenance Risk (1-5)**:
  - 1: Standard Kotlin/Compose code with automated tests.
  - 5: High regression hazard or third-party native dependency burden.

---

## 3. Evidence Confidence Levels

Every priority decision records the confidence level of the underlying evidence:

| Level | Definition | Standard of Proof |
| :--- | :--- | :--- |
| **High Confidence** | Confirmed reproduction with synthetic fixture or verified diagnostic log. | Passes in automated test environment or verified across multiple physical devices. |
| **Medium Confidence** | Detailed technical report with specific codec, container, and OS details. | Plausible defect matching known platform limitations; reproduction in progress. |
| **Low Confidence** | Single user description lacking reproduction steps or logs. | Awaiting clarification from reporter. |
| **Hypothesis Only** | Engineering assumption or speculative improvement. | Requires validation before scheduling. |
