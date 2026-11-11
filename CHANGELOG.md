# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Changed
- Build migrated to the IntelliJ Platform Gradle Plugin 2.x (`org.jetbrains.intellij.platform`) on Gradle 9.
- Compatible with IntelliJ Platform builds 241 (2024.1) to 263.* (2026.3); the open-ended `999.*` upper bound is gone.
- The JDK is chosen by Gradle toolchains (Java 17); no machine-specific `java.home` any more.

### Added
- Plugin Verifier (`./gradlew verifyPlugin`) against 2024.1.1 and 2026.3.
- GitHub Actions workflow that builds, tests and verifies the plugin.
- Licence headers on every source file.

## [0.1.0]

### Added
- `.fl`, `.fth` and `.forge` file type with a lexer, a composite PSI and a parser definition for the FL language.
- Syntax highlighting (including stack-effect headers, parameters and META clauses) and a colour settings page.
- Name resolution across files and into the built-in words (parameter, current vocabulary, `USING:` imports, `core`,
  qualified `vocab:name`, `META:` targets), backed by a file-based index.
- Built-in words as navigation targets, generated from a Forge checkout (`tools/export-builtins.mjs`).
- Find usages, rename, and completion of the words visible at the caret (including vocabulary names).
- Diagnostics that name the spec error code, with a quick fix for ambiguous names, and two weak-warning inspections.
- Quick documentation, Go to Symbol, structure view, breadcrumbs, folding, typed pairing and quote handling,
  semantic colours and TODO indexing.
- Unit tests for the lexer, PSI, resolution, rename, completion and diagnostics, plus an opt-in corpus check
  (`-Dfl.corpus=...`).
