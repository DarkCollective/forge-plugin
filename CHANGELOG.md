# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added (op test vectors)
- The **FL Vectors** run configuration: runs the op test vectors and check pairs of `packages/ops/vocab/*.test.json` in
  the test runner window, grouped by file and op, with diffs of expected and actual values, navigation to the JSON
  entry and to the op, and Rerun Failed Tests. A whole file also gets a `coverage` test with the CI rules.
- Ways to run: ▶ icons in vectors files (the file, each entry with its last result), *Run '<op> vectors'* in the ▶
  menu of a word, and *Run* on a vectors file, a vocabulary's `.fth` file or the `vocab` directory
  (`FLVectorsConfigurationProducer`). The icons are registered for the JSON language by id; no JSON plugin class is
  used.
- `forge-vectors.mjs`: uses `runOpVectors` of `@forge/worker` for the verdict, one vector at a time, and prints
  TeamCity service messages.
- `FLVectorsTest`: entry scanning, the configuration, Rerun Failed, the producer, locations, the gutter icons, and
  (with `-Dfl.forge`) the runner against Forge and against a copy with broken vectors, parsed by the platform's test
  event converter.

### Added (Set Value while debugging)
- Set Value on locals of any frame and on data stack entries of a paused FL debug session. The new value is an FL
  expression evaluated in the frame that must leave exactly one value; a header input keeps its declared type and
  constraint. The frames are refreshed in place, so the selected frame stays selected.
- The runner's `setValue` command and `valueSet` event (with the frames and stack after the change), and
  `canSetValues` on `paused`. They use `StepEvent.setStackItem` and `DebugFrame.setLocal`, additive `@forge/lang`
  APIs; with a Forge without them, no value offers Set Value.
- Tests: `FLDebugTest` (modifiers only when the engine can, commands, refresh) and `testRunnerSetValue` in
  `FLDebugProcessHandlerTest` (with `-Dfl.forge`; skipped against a Forge without the setters).

### Added (evaluating FL while debugging)
- Evaluate Expression, watches and hover in a paused FL debug session. The expression is body code of the selected
  frame: its locals by name, a copy of the paused data stack, the program's variables, and the frame's `IN:` and
  `USING:` scope. The result node shows the new values top first (one value as itself), how many paused values
  the expression took, and what it printed. Hover is limited to parameters and `:>` locals of the paused word.
- The runner's `evaluate` command and `evaluated` event: the expression is compiled as the body of a throwaway word
  whose transaction is rolled back (nothing can be defined), and run with its own limits (10 million steps, 5 s).
  The run and its evaluations now share one variable store.
- The expression editor is an FL fragment (highlighting, completion) that diagnostics skip
  (`FLFile.DEBUGGER_FRAGMENT`).
- Tests: `FLDebugTest` (scope sent, local shortcut, results, errors, hover ranges, fragment), and evaluations in the
  scripted session of `FLDebugProcessHandlerTest` (with `-Dfl.forge`).

### Added (debugging FL)
- An FL debugger: line breakpoints (hit on each entry of the line, also per pass of a loop or quotation), step
  into/over/out by line, run to cursor, pause, stop, frames of words and quotations, locals and the data stack.
  Debug from the gutter menu of a word or with the Debug executor on any FL run configuration.
- The runner's debug mode: a JSON-lines protocol on stdin and marked event lines on stdout (`FLDebugProcessHandler`
  keeps them out of the console). Locals come from `StepEvent.frames()`, an additive `@forge/lang` API; without it
  the debugger shows the innermost frame only.
- `FLDebugTest`, `FLDebugProcessHandlerTest` (with `-Dfl.forge`, a scripted debug session against a real checkout).

### Changed (FL v0.2, Forge main of 16 November 2026)
- `checks` is no longer reserved: `checks <input|output> <test> <arguments> ;` with the tests `regex`, `charset`,
  `entropy`, `magic-bytes` (hex and offset, or `format`), `printable`, `utf8` and `binary`, validated as Forge's
  `meta-checks.ts` does (same reasons), and at most 8 per word. Number tokens are allowed in this clause only.
- New `META:` clause `expects "<what>" ;` (the `use-when` rules, at most 64 characters).
- `builtins.json` regenerated: `base64:_sextets`, `base64:_unsextets`, `magic:_identity` and `magic:_search` are new
  primitives; `base64` and `hex` encode/decode and `list` sort, sort-by and shuffle are FL words again.
- `FLCorpusTest` lists misplaced `META:` blocks (`FL-META-PLACEMENT`) without failing.

### Added (running FL)
- A ▶ gutter icon on every word: a dialog with one field per header input (defaults filled in, a combo box for
  `choice` and `bool` inputs), then a run through a temporary run configuration named after the word.
- The **FL** run configuration: runs any FL console source in a fresh runtime built from a built Forge checkout, with
  a current vocabulary, extra user-unit files, and step and time limits. The console shows the output, the result
  stack, steps, time and effects; error frames link to `file.fth:line:col`.
- `forge-runner.mjs`, the Node.js runner the plugin starts (Node 20 or newer), and `FLRunTest`
  (`-Dfl.forge=/path/to/forge` runs it against a real checkout).

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
