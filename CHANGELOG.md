# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added (FL v0.2)
- Lexer and parser: the keywords `MEMO:`, `CHOICE:` and `:>` (standalone only; `MEMO:foo` stays a qualified name),
  the header after `MEMO: name`, `CHOICE:` blocks with `option` clauses, `:>` bindings, `name:choice<type>` header
  types, and the `META:` clauses `use-when`, `link`, `inverse`, `helper` and the reserved `checks`.
- `:>` locals: resolution (innermost body first, then the header), rename, find usages, completion and highlighting.
- Shared choice types: a namespace of their own in the index (`T` keys), resolution of `choice<…>` (6.8), completion,
  go to definition, find usages, rename, structure view, folding and breadcrumbs.
- Diagnostics for `exit` placement, `:>` placement and duplicates, `MEMO:` header and direct purity, `choice<…>`
  references and defaults, `CHOICE:` options, `use-when`, `link`, `inverse` and `checks`.
- `link` URLs are web references.
- The 53 new `core` words, including the twenty FL words of the core prelude (exported with kind `prelude`).
- docs/FL-STYLE.md, the FL style guide, and `FLStyleGuideTest`, which fails when the formatter and an example of
  the guide disagree.
- An FL code style page with a default indent of two spaces, the Forge house style.
- docs/RUN-DEBUG-RESEARCH.md: feasibility of running and debugging FL words from the IDE.

### Fixed
- The formatter put the contents of a multi-line quotation or list at column 0; they are now one level deeper than
  the line that opens the bracket.
- The formatter removed the blank line between comment paragraphs (a section heading and the comment below it) and
  moved end-of-line comments to a line of their own; it now keeps at most one blank line and keeps end-of-line
  comments where they are.
- The formatter forced a blank line between one-line declarations (`VARIABLE` tables, one-line words); such runs
  may now stay together.
- Continuation lines of a long `USING:` are indented.
- `tools/export-builtins.mjs` failed on a v0.2 Forge checkout (the core prelude words have no source unit).

### Changed
- Build migrated to the IntelliJ Platform Gradle Plugin 2.x (`org.jetbrains.intellij.platform`) on Gradle 9.
- Compatible with IntelliJ Platform builds 241 (2024.1) to 263.* (2026.3); the open-ended `999.*` upper bound is gone.
- The JDK is chosen by Gradle toolchains (Java 17); no machine-specific `java.home` any more.

### Added
- FL code formatter for top-level forms, definition bodies, quotations/lists and `META:` clauses.
- Formatter removes extra trailing blank lines while preserving an existing final newline.
- Intention to move a `META:` block next to the colon definition it annotates.
- Code Cleanup inspection and quick fix for relocating misplaced `META:` blocks.
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
