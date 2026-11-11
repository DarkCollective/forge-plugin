# Forge FL IntelliJ Plugin

This is a starter IntelliJ plugin for the FL language described in `docs/LANGUAGE.md`.

## Features

- `.fl` / `.fth` file type support, with a lexer and parser that follow `docs/LANGUAGE.md` sections 2 and 5
- syntax highlighting, including stack-effect headers and parameters
- name resolution as in `docs/LANGUAGE.md` section 6, across files and into the built-in words: parameter, current
  vocabulary (`IN:`), imports (`USING:`, an ambiguous name is a poly-variant reference with the candidates in
  `USING:` order), then `core`; qualified `vocab:name` (a private `_name` only from its own vocabulary); `META:`
  targets and `alias`/`see-also` clauses per 5.9. An unresolved name stays a soft reference (no diagnostics yet)
- Ctrl-click, find usages and rename (Shift+F6) for words, constants, variables (`n` and `n!`), aliases, header
  parameters, `META:` targets and `param` clauses, across all files of the project; symbol names such as `+!` and
  `>hex` work. Only uses that resolve to the declaration change: a same-named word of another vocabulary, a
  shadowing parameter or a shadowing word of the current vocabulary is left alone
- built-in words (core words, intrinsics and the TypeScript primitives bound into vocabularies) are real
  navigation targets: Ctrl-click on `dup` opens a read-only `core.fl` with the stack effect and documentation.
  They cannot be renamed
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
`fl`, `constant` and `variable` words come from a `.fth` unit. At run time the plugin writes one read-only
`<vocabulary>.fl` per vocabulary into the IDE system directory (`forge-fl/builtins-<hash>`) and exposes the directory
as a library root, so the files are indexed like any FL file. Each word is an empty colon definition whose header is
the stack effect, under a `\` comment with its documentation and a `[TypeScript ...]` marker.

## Index

`FLIndex` (a file-based index, `src/main/java/com/forge/fl/index`) holds per file: declarations keyed
`D<vocab>:<name>` (words, constants, variables with `n!`, primitives, aliases, private ones included), word uses keyed
`U<bareName>` and the vocabularies the file switches to or declares into, `V<vocab>`. It is computed from the token
stream alone (`FLLightScan`); bump `FLIndex.VERSION` when the format or the scanning rules change.

## Tests

`./gradlew test`. To parse a directory of real sources and check that every word reference in it resolves:
`./gradlew test -Dfl.corpus=/path/to/forge/packages/ops/vocab` (prints `FL-RESOLVE files=.. refs=.. unresolved=..
ambiguous=..`; the run fails when either count is not zero).

## Run locally

```bash
./gradlew runIde
```

## Build

```bash
./gradlew build
```
