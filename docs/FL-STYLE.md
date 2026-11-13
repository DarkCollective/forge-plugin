# FL style guide

The house style for FL source (`.fth`, `.fl`), for FL v0.2 ([LANGUAGE.md](../../forge/docs/LANGUAGE.md) in the
Forge repository). It applies to all FL code: written by hand, by an agent, or reformatted by the IntelliJ plugin.

**How this guide and the plugin stay in agreement.** Each rule says who keeps it:

- **Formatter**: *Code | Reformat Code* in the plugin applies it. Formatting is idempotent.
- **Inspection**: the plugin flags a violation and offers a fix (*Code | Code Cleanup* applies it).
- **Convention**: no tool checks it; reviewers and agents keep it.

Every example in a ```` ```fl ```` block below is a test fixture (`FLStyleGuideTest`): the build fails if the
formatter would change it. Examples of what *not* to write are in ```` ```text ```` blocks and are not checked.
If you change a formatter rule, change this guide in the same commit (the test makes you).

## 1. Files

| Rule | Kept by |
|---|---|
| UTF-8, `\n` line endings, spaces only (never tabs) | Formatter (indentation), convention |
| The file starts with the licence comment, then a comment describing the vocabulary, then `IN:` on the next line | Convention |
| One `IN:` per file unless the file deliberately spans vocabularies | Convention |
| `USING:` follows `IN:` after one blank line | Formatter |
| No trailing whitespace; no blank lines at the end of the file; the file ends with exactly one newline | Formatter (it trims; it does not add a missing final newline) |

```fl
\ Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
\ demo: what this vocabulary is for, in a sentence or two.
IN: demo

USING: util ;

: answer ( -- int ) 42 ;
```

## 2. Indentation and spacing

| Rule | Kept by |
|---|---|
| Indent with **2 spaces** per level (the plugin's default; *Settings | Editor | Code Style | FL*) | Formatter |
| Exactly one space between tokens; no column alignment inside code | Formatter |
| Line breaks inside a body are the author's; blank lines inside a body are removed | Formatter |
| Aim for lines of at most 120 characters; long data literals (tables of strings or numbers) are exempt | Convention |

```text
  acc  b "data" get codec _block-bytes  schema  avro:_decode  cat      \ no: double spaces are collapsed
```

## 3. Top-level layout

| Rule | Kept by |
|---|---|
| One blank line between top-level forms | Formatter |
| A run of **one-line** forms (`CONSTANT`, `VARIABLE`, `PRIMITIVE`, one-line definitions) may stand without blank lines between them; at most one blank line is kept | Formatter |
| A comment run directly above a definition or a `CHOICE:` block is its documentation: one blank line before the comment, none between the comment and the form | Formatter |
| Section headings are line comments `\ ---- name ----`, with at most one blank line around them | Formatter (keeps at most one) |
| No executable code outside definitions in a source unit | Inspection (`E_TOPLEVEL`) |

```fl
IN: demo

0 VARIABLE _count
"" VARIABLE _last
256 CONSTANT block-size

: _a ( -- num ) 6378137.0 ;
: _f ( -- num ) 1.0 298.257223563 / ;

\ ---- encode ----

\ The documentation of _pad: it sits right above the definition.
: _pad ( s:str n:int -- str )
  s n "0" util:pad-left ;
```

## 4. Definitions

| Rule | Kept by |
|---|---|
| `: name ( header )` on one line; one space around `(`, `)` and between header items; `name:type` without spaces | Formatter |
| Leading `"…" doc` and `"…" category` declarations each on a line of their own, right after the header | Formatter |
| The body starts on the line after the header (or, for a short word, on the header line), indented one level | Formatter |
| `;` ends the last line of the body, after one space; it is not put on a line of its own | Convention (the formatter keeps the author's line break) |
| No blank lines inside a definition | Formatter |
| `MEMO:` definitions follow the same layout as `:` | Formatter |
| Public words have a named, typed header (strict mode); see LANGUAGE.md §5.4 | Diagnostics (`E_HEADER`) |

```fl
: encode ( bytes alphabet:choice{standard|hex}=standard padding:bool=true -- str )
  "Encode bytes as Base32 text (RFC 4648 sections 6 and 7)." doc
  "Encodings" category
  alphabet _alphabet str>bytes padding _encode ;

MEMO: _table ( -- record )
  _entities "\n" split [ "=" split ] map >record ;
```

## 5. Quotations and list literals

| Rule | Kept by |
|---|---|
| One space inside the brackets: `[ x 1 + ]`, `{ 1 2 3 }` (the language requires the space) | Formatter |
| A quotation or list that spans lines: its contents are one level deeper than the line that opens it, and the closing bracket lines up with that line | Formatter |
| The two branches of an `if` either both fit on the condition's line, or each goes on a line of its own | Convention |
| Choose between several cases with `cond` or `case` (v0.2), not with a chain of nested `if`s | Convention (the formatter indents every nested quotation, so a chain walks to the right) |
| End-of-line comments are allowed (useful in data tables) and keep their alignment | Formatter |

```fl
: _sign ( s:str -- bool str )
  s len 0 > [
    s 0 1 slice dup "-" =
    [ drop true s 1 s len slice ]
    [ "+" = [ false s 1 s len slice ] [ false s ] if ] if
  ] [ false s ] if ;

: _strokes ( -- list )
  { { "\u{D8}" "O" } { "\u{F8}" "o" }       \ LATIN CAPITAL/SMALL LETTER O WITH STROKE
    { "\u{110}" "D" } { "\u{111}" "d" }     \ LATIN CAPITAL/SMALL LETTER D WITH STROKE
  } ;
```

Prefer `cond` to an `if` chain (the list is test/action pairs; a final unpaired quotation is the default):

```text
  mode "CBC" = [ data _cbc ] [
  mode "CFB" = [ data _cfb ] [
  data _ctr ] if ] if ;                    \ no: hard to read, and the formatter indents each level
```

```fl
: _run ( data:bytes mode:str -- bytes )
  {
    [ mode "CBC" = ] [ data _cbc ]
    [ mode "CFB" = ] [ data _cfb ]
    [ data _ctr ]
  } cond ;
```

## 6. Locals (`:>`)

| Rule | Kept by |
|---|---|
| Bind with `:>` at the end of the line that computes the value: `days 719468 + :> z` | Convention |
| Prefer a header name or a `:>` local to stack shuffling (`rot`, `over`, `pick`) when a value is used more than twice | Convention |
| Do not reuse a name already visible in the body; do not name a local like a word you need | Diagnostics (`E_REDEFINED`, `W_SHADOW`) |

```fl
: civil-from-days ( days:int -- int )
  days 719468 + :> z
  z 146097 div :> era
  z era 146097 * - ;
```

## 7. `META:` blocks

| Rule | Kept by |
|---|---|
| A `META:` block comes **immediately after** the definition it annotates, after one blank line | Inspection (*Misplaced META: block*, with a *Move META block next to definition* fix that Code Cleanup applies) |
| `META: name` on one line; one clause per line, indented one level; the closing `;` alone at column 0 | Formatter |
| Clause order: `doc`, `category`, `version`, `use-when`, `param` (in header order), `alias`, `inverse`, `helper`, `see-also`, `link` | Convention |
| Short documentation may stay in the leading `"…" doc` / `"…" category` of the definition; do not set the same field in both places (it is `E_META` at load time) | Convention |
| `use-when`: one sentence, no line break, at most 160 characters, no leading or trailing space | Diagnostics (`E_META`) |
| `link`: an `https://` URL that follows LANGUAGE.md §5.9, optional label of at most 80 characters, at most 8 per word | Diagnostics (`E_META`) |

```fl
: rot ( str amount:int[1..25]=13 -- str )
  amount _rotate ;

META: rot
  doc "Rotate each Latin letter by a fixed amount." ;
  category "Classical ciphers" ;
  use-when "You need ROT13 or another Caesar shift of Latin letters." ;
  param amount "Shift (13 = ROT13)" ;
  alias rot13 ;
  inverse rot ;
  see-also classical:atbash ;
  link "https://en.wikipedia.org/wiki/Caesar_cipher" "Caesar cipher" ;
;
```

## 8. `CHOICE:` blocks

| Rule | Kept by |
|---|---|
| Same shape as `META:`: `CHOICE: name`, one `option` per line indented one level, closing `;` at column 0 | Formatter |
| A comment directly above the block documents it (as for a definition) | Formatter |
| Give each option a help string when its meaning is not obvious from the name | Convention |
| Use a shared choice type (`choice<name>`) when two or more words take the same set of options | Convention |

```fl
\ Block cipher padding.
CHOICE: padding
  option pkcs7 "PKCS#7 padding (RFC 5652, section 6.3)." ;
  option none "No padding: the input length must be a multiple of the block size." ;
;

: pad ( data:bytes p:choice<padding>=pkcs7 -- bytes )
  data p _pad ;
```

## 9. Comments

| Rule | Kept by |
|---|---|
| Use `\` line comments; keep `( … )` block comments for short notes inside a line | Convention |
| A line comment starts with `\` and one space | Convention |
| Comments on their own line are indented like the code they describe | Formatter |
| Write full sentences that explain *why*; do not restate the stack effect | Convention |

## 10. Naming

These come from the language (LANGUAGE.md §2.11, §5.4, §5.7) and Forge practice.

| Rule | Kept by |
|---|---|
| Words, constants, variables, choice types: lower-kebab-case (`decode-table`) | Convention |
| Private helpers start with `_` (`_crypt`); a private word has no `META:` block | Diagnostics (`E_META`) |
| Predicates end in `?` (`space?`); conversions are `a>b` (`str>bytes`); a variable's setter is `n!` | Convention |
| Parameter names: lower case, digits and `-` (`iv`, `key-size`) | Diagnostics (`E_HEADER`) |
| Vocabulary names: lower case (`base64`, `aes-kw`) | Convention |
