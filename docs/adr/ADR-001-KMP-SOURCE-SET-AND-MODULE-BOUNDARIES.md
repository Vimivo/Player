# ADR-001: Kotlin Multiplatform Source-Set & Module Boundaries

## Status
Accepted

## Context
Vimivo is a privacy-first, offline-first music and video player targeting five operating systems: Android, iOS/iPadOS, macOS, Windows, and Linux. As the application evolved through Milestones 1 to 34, functionality was partitioned across Gradle modules (`:shared`, `:androidApp`, `:desktopApp`) and native platform projects (`iosApp`).

A core architectural challenge in Kotlin Multiplatform is preventing platform-specific APIs (such as Android Media3, Java JDBC/AWT, or Apple Cocoa/AVFoundation) from leaking into portable common code (`commonMain`), while avoiding an over-engineered module explosion that slows compilation and harms maintainability.

## Decision
1. **Preserve Cohesive Three-Module Structure**: Retain `:shared`, `:desktopApp`, and `:androidApp`. Reject creating dozens of micro-modules without measurable build or ownership justification.
2. **Strict Dependency Direction**:
   - `commonMain` contains only portable domain models, playback contracts, lyrics parsers, playlist handlers, and platform-neutral error models.
   - UI layers depend on domain contracts, never vice versa.
   - Platform implementations implement shared contracts via dependency inversion.
3. **Desktop JVM Consolidation**: macOS, Windows, and Linux are distributed from the single `:desktopApp` JVM target, selecting platform adapters (Cocoa, Win32, X11, MPRIS) dynamically at runtime. Native targets (`mingwX64`, `linuxX64`) are not introduced for desktop logic.
4. **Automated Architecture Boundary Testing**: Enforce static boundary rules via `ArchitectureBoundaryTest`:
   - `shared/src/commonMain` must have zero imports of `java.sql`, `java.io`, `java.nio`, `javax.*`, `android.*` (excluding JetBrains KMP Compose/Navigation), or platform-specific packages.
   - `androidApp` must not import desktop code.
   - `desktopApp` must not import Android code.

## Consequences & Tradeoffs
- **Positive**: Compilation remains extremely fast (under 300ms incremental build with configuration cache). Clean boundaries ensure shared code compiles cleanly to iOS native framework (`iosArm64`, `iosSimulatorArm64`).
- **Negative**: Platform adapters must be maintained in their respective platform modules, requiring explicit interface implementations in shared contracts.

## Validation
Verified by `ArchitectureBoundaryTest` passing 100% in automated CI test runs.
