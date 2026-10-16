# Vimivo Feedback-to-Fix Development Lifecycle

## 1. Overview & Traceability Standard

Every user-reported bug or playback defect in Vimivo follows an unbroken traceability chain:

```
[1. User Feedback Report]
           │
           ▼
     [2. Triage & Deduplication]
           │
           ▼
     [3. Synthetic Reproduction Fixture]
           │
           ▼
     [4. Root-Cause Analysis]
           │
           ▼
     [5. Focused Fix Implementation]
           │
           ▼
     [6. Automated Regression Test]
           │
           ▼
     [7. Multi-Platform Verification Gate]
           │
           ▼
     [8. Documented Resolution in Release Notes]
```

---

## 2. Step-by-Step Execution Protocol

### Step 1: Intake & Triage
- Report is reviewed by an engineer for version, platform, and reproduction steps.
- If incomplete, the report is assigned `status:needs-information` and clarifying questions are posted using standardized community response templates.

### Step 2: Reproduction & Minimal Fixture Creation
- The engineer attempts reproduction in a clean VM or test environment.
- If reproduction succeeds, a minimal, non-confidential test fixture is constructed (e.g., a 2-second synthesized audio tone or 10-frame test pattern).
- Never request or commit copyrighted or private user media files.

### Step 3: Root-Cause Investigation
- Isolate whether the defect is in:
  - **Shared Domain**: Logic flaw in state machine, sorting, or playlist calculations (`shared/commonMain`).
  - **Native Decoder**: Platform capability constraint or misconfigured backend options (`MpvPlayerEngine`, `Media3PlayerEngine`).
  - **Persistence**: SQLite query defect, foreign key constraint, or migration bug (`dev.vimivo.desktop.library`).
  - **UI / Presentation**: Compose Multiplatform recomposition glitch, focus trap, or accessibility semantics gap.

### Step 4: Focused Fix & Regression Test
- Implement the minimal, robust fix that directly resolves the root cause.
- **Mandatory**: Add an automated regression test that fails without the fix and passes with it.
- Never mark a defect fixed without a verifying regression test.

### Step 5: Verification & Release Note Association
- Run `./scripts/verify_all.sh` across all target modules (:shared, :desktopApp, :androidApp).
- Update the bug triage register (`docs/BUG_TRIAGE.md`) and link the commit to the upcoming release notes.
