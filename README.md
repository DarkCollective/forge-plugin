# Forge FL IntelliJ Plugin

This is a starter IntelliJ plugin for the FL language described in `docs/LANGUAGE.md`.

## Features

- `.fl` / `.fth` file type support, with a lexer and parser that follow `docs/LANGUAGE.md` sections 2 and 5
- syntax highlighting, including stack-effect headers and parameters
- rename (Shift+F6), find usages and Ctrl-click for words, constants, variables, `n!`, header parameters,
  `META:` targets, `param` clauses and aliases, within one file (cross-file and core words are a later phase)
- structure view, brace matching, `\ ` line comments, completion of keywords and declarations in the file
- color settings page for FL syntax colors

## Tests

`./gradlew test`. To parse a directory of real sources as well: `./gradlew test -Dfl.corpus=/path/to/vocab`.

## Run locally

```bash
./gradlew runIde
```

## Build

```bash
./gradlew build
```
