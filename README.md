# Forge FL IntelliJ Plugin

This is an IntelliJ plugin for the FL language, **v0.2**, described in `docs/LANGUAGE.md` of the Forge repository
(its Appendix A lists what v0.2 added). The house formatting style is [docs/FL-STYLE.md](docs/FL-STYLE.md); the
formatter follows it and a test keeps the two in agreement. Running and debugging FL from the IDE is researched
in [docs/RUN-DEBUG-RESEARCH.md](docs/RUN-DEBUG-RESEARCH.md).

## Features

- `.fl` / `.fth` file type support, with a lexer and parser that follow `docs/LANGUAGE.md` sections 2 and 5,
  including the v0.2 forms: `MEMO:` definitions, `CHOICE:` blocks with `option` clauses, `:>` mid-body locals,
  `name:choice<type>` header types, and the `META:` clauses `use-when`, `link`, `inverse`, `helper` (and the
  reserved `checks`)
- syntax highlighting, including stack-effect headers and parameters
- code formatting in the style of [docs/FL-STYLE.md](docs/FL-STYLE.md): top-level forms, definition bodies,
  multi-line quotations/lists, `META:` and `CHOICE:` blocks, end-of-line comments; the default indent is two
  spaces (*Settings | Editor | Code Style | FL*), and extra trailing blank lines are removed
- the “Move META block next to definition” intention for relocating a block to its resolved colon word
- an enabled-by-default, Code Cleanup-compatible inspection for misplaced `META:` blocks; in the Reformat File
  dialog, enable **Code cleanup** to apply its move fix together with the formatter
- `:>` locals resolve, rename and highlight like parameters, scoped to their body or quotation (7.1);
  `choice<type>` resolves to its `CHOICE:` block (6.8: current vocabulary, then imports; `vocab:type`), with
  completion, go to definition, find usages and rename. Choice types have their own namespace
- `link` URLs in `META:` blocks are web references (Ctrl-click opens them)
- name resolution as in `docs/LANGUAGE.md` section 6, across files and into the built-in words: parameter, current
  vocabulary (`IN:`), imports (`USING:`, an ambiguous name is a poly-variant reference with the candidates in
  `USING:` order), then `core`; qualified `vocab:name` (a private `_name` only from its own vocabulary); `META:`
  targets and `alias`/`see-also` clauses per 5.9. An unresolved name stays a soft reference; the diagnostics below report it
- Ctrl-click, find usages and rename (Shift+F6) for words, constants, variables (`n` and `n!`), aliases, header
  parameters, `META:` targets and `param` clauses, across all files of the project; symbol names such as `+!` and
  `>hex` work. Only uses that resolve to the declaration change: a same-named word of another vocabulary, a
  shadowing parameter or a shadowing word of the current vocabulary is left alone
- built-in words (core words, intrinsics and the TypeScript primitives bound into vocabularies) are real
  navigation targets: Ctrl-click on `dup` opens a read-only `core.fl` with the stack effect and documentation.
  They cannot be renamed
- diagnostics naming the spec error code (`FLDiagnostics`): E_UNDEFINED (also use before definition and a word inside
  its own body), E_NO_VOCAB (qualified names and `USING:`), E_AMBIGUOUS (with a quick fix per candidate that writes
  `vocab:name`), E_PRIVATE, E_REDEFINED (words, constants, variables and `n!`, aliases), E_NAME, E_HEADER (no header,
  no `--`, duplicate, reserved or malformed parameter name), E_META (undefined or later target, private target,
  unknown clause, `param` that is not a parameter other than the first input, `checks`, `use-when` text rules,
  `link` URL grammar, duplicates and the 8-link cap, `inverse` outside the current vocabulary, private or undefined),
  and for v0.2: `exit` outside a body or a quotation passed to if/when/unless/while/loop (E_SYNTAX), `:>` outside a
  definition or inside `{ }` (E_SYNTAX), a duplicate local (E_REDEFINED), a `MEMO:` header other than `( -- T )`
  (E_HEADER), `recurse` in a `MEMO:` body, a `MEMO:` body that uses a variable, `.`, `.s` or a session word
  directly (E_FORBIDDEN), `choice<…>` that does not resolve or is ambiguous, a default that is not one of the
  type's options, duplicate or over-long options, a `CHOICE:` redefined in the file. Errors are an annotator; the weak
  warnings E_TOPLEVEL / `VOCAB` in a file and W_SHADOW are two inspections that can be switched off. Not checked:
  types, constraints and defaults of headers, stack effects, repeated `doc`/`category`/`version`, string lengths,
  alias collisions with words of other files, `IN: core`, `PRIMITIVE` registry checks, `MEMO:` purity through
  callees and effect labels (+async, +random, +time), `emit` outside a collector (a run-time rule)
- quick documentation (qualified name, kind, effect, doc, category, version, parameters with META help, aliases,
  see-also, constant/variable value; built-ins from their comment lines), also in the completion popup
- Go to Symbol (index-backed; names are `vocab:name`, so `encrypt` and `aes:enc` both match; built-ins with
  "include non-project items"), structure view grouped by vocabulary when a file has several `IN:`, with a sorter and
  a "show private words" filter, breadcrumbs
- folding (definitions, META: blocks, multi-line quotations and lists, runs of 3+ comment lines, licence header),
  typed pairing (`[  ]`, `{  }` with spaces inside, `""`), semantic colours (META clause words, declaration names,
  parameter uses, built-in uses, vocabulary names) on the colour page, TODO in comments
- structure view, brace matching, `\ ` line comments
- completion of what is visible at the caret (parameters, current vocabulary, imported vocabularies, `core`) with the
  stack effect as tail text and the vocabulary as type text; `vocab:` offers that vocabulary's words; `USING:` and
  `IN:` offer vocabulary names
- color settings page for FL syntax colors

## Built-in words

`src/main/resources/builtins/builtins.json` lists every word that no `.fth` source defines: the `core` words, the
intrinsics and the TypeScript primitives that the ops package binds into vocabularies (private ones and aliases
included). It is generated from a built Forge checkout:

```bash
cd /path/to/forge && npm run build                 # packages/lang/dist and packages/ops/dist must exist
node tools/export-builtins.mjs /path/to/forge      # rewrites builtins.json (plain Node ESM, no dependencies)
node tools/export-builtins.mjs /path/to/forge --check   # exit 1 if builtins.json is out of date
```

A word is TypeScript-defined when its `WordEntry.kind` is `primitive` or `intrinsic` (its `meta.source` is empty);
`fl`, `constant` and `variable` words come from a `.fth` unit. The one exception is the v0.2 core prelude (twenty
FL words such as `sum` and `starts-with?`, compiled from a string inside `packages/lang`): they have no source file,
so they are exported too, with kind `prelude` and the marker `[FL prelude ...]`. At run time the plugin writes one read-only
`<vocabulary>.fl` per vocabulary into the IDE system directory (`forge-fl/builtins-<hash>`) and exposes the directory
as a library root, so the files are indexed like any FL file. Each word is an empty colon definition whose header is
the stack effect, under a `\` comment with its documentation and a `[TypeScript ...]` marker.

## Index

`FLIndex` (a file-based index, `src/main/java/com/forge/fl/index`) holds per file: declarations keyed
`D<vocab>:<name>` (words, constants, variables with `n!`, primitives, aliases, private ones included), choice types
keyed `T<vocab>:<name>`, word and choice-type uses keyed `U<bareName>` and the vocabularies the file switches to or
declares into, `V<vocab>`. It is computed from the token
stream alone (`FLLightScan`); bump `FLIndex.VERSION` when the format or the scanning rules change.

## Requirements

- Any JDK 17 or newer to run Gradle (a `JAVA_HOME` is not needed; the build sets none). Compiling and testing use a Java 17
  toolchain, which Gradle downloads on first use (foojay resolver) if it is not installed.
- The Gradle wrapper (9.9.1); the IntelliJ Platform Gradle Plugin 2.x needs Gradle 9 or newer.
- Compiled against IntelliJ IDEA Community 2024.1.1. The plugin declares compatibility with builds 241 to 263.*
  (2024.1 to 2026.3); `verifyPlugin` checks both ends. Raise the upper bound in `build.gradle.kts` only after
  verifying the next release.

## Tests

`./gradlew test`. With `-Dfl.corpus=...` it also prints `FL-DIAG files=.. errors=.. warnings=.. weak=..` (fails on errors or warnings). To parse a directory of real sources and check that every word reference in it resolves:
`./gradlew test -Dfl.corpus=/path/to/forge/packages/ops/vocab` (prints `FL-RESOLVE files=.. refs=.. unresolved=..
ambiguous=..`; the run fails when either count is not zero). Without the property the corpus test does not run.

## Run locally

```bash
./gradlew runIde
```

## Build and verify

```bash
./gradlew clean test buildPlugin   # plugin zip in build/distributions
./gradlew verifyPlugin             # Plugin Verifier against IC 2024.1.1 and IU 2026.3 (downloads the IDEs, ~1 GB, outside the repo)
```

## CI

`.github/workflows/build.yml` runs on every push and pull request: `build`, `buildPlugin` and `verifyPlugin`, with the
plugin zip, test reports and verifier reports uploaded as artifacts. It does not sign or publish anything.

## Licence

Apache License 2.0, see `LICENSE.txt` and `NOTICE` (both are also packaged in the plugin jar under `META-INF`).
See `CHANGELOG.md` for changes.
