# Contributing to Vimivo

Thank you for your interest in contributing to Vimivo! This document outlines our development process, architectural boundaries, coding standards, and pull request workflow.

---

## 1. Architectural Boundaries & Principles

Vimivo maintains strict Clean Architecture and multiplatform boundaries:
- **Shared Domain (`shared/src/commonMain`)**:
  - Contains platform-neutral models, business rules, repository contracts, and playback interfaces.
  - **Zero Platform Classes**: Never import `java.io.*`, `java.sql.*`, `android.*`, `UIKit.*`, or `AVFoundation.*` in `commonMain`.
- **Desktop Application (`desktopApp`)**:
  - Contains Compose Desktop presentation for macOS, Windows, and Linux.
  - Native libmpv client integration lives here.
- **Android Application (`androidApp`)**:
  - Contains Android-specific APIs (Media3, MediaStore, DataStore, Room, Foreground Service, Notifications).
- **iOS Application (`iosApp`)**:
  - Native SwiftUI application consuming `VimivoShared.framework`.
- **Non-Destructive Defaults**:
  - Never automatically delete user files or overwrite existing files without explicit user consent.

---

## 2. Development Setup & Verification

Before opening a pull request or submitting code:
1. Ensure JDK 17 and Android SDK 36 are installed.
2. Run the full developer validation gate:
   ```shell
   ./scripts/verify_all.sh
   ```
   Or via Gradle:
   ```shell
   ./gradlew verifyAll
   ```
3. Ensure Detekt passes with **0 weighted violations**.

---

## 3. Git Branching & Commit Conventions

- **Branch Naming**:
  - `feat/feature-name` for new functionality
  - `fix/bug-description` for defect remediation
  - `docs/doc-update` for documentation changes
  - `test/test-suite` for test additions
- **Commit Messages**:
  Follow the Conventional Commits specification:
  - `feat(scope): concise description of feature`
  - `fix(scope): concise description of bug fix`
  - `test(scope): description of added test suite`
  - `docs(scope): description of documentation update`
  - `refactor(scope): internal refactoring with no behavioral change`
  - `chore(scope): build, dependency, or tooling maintenance`

---

## 4. Database Migration Rules

When modifying the database schema:
1. Increment `DesktopDatabase.SCHEMA_VERSION`.
2. Add a new `migrateToVersionN(statement)` method in `DesktopDatabase.kt`.
3. Never alter or delete existing migration blocks.
4. Add comprehensive upgrade tests in `DesktopDatabaseMigrationTest.kt` verifying that pre-existing user records, playlists, favorites, and foreign keys remain intact.

---

## 5. Pull Request Process

1. Fork the repository and create your branch from `main`.
2. Implement your changes in small, focused commits.
3. Add automated tests covering new business logic or regression cases.
4. Validate that `./scripts/verify_all.sh` succeeds.
5. Submit your pull request using the provided [Pull Request Template](.github/pull_request_template.md).
