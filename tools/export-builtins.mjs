#!/usr/bin/env node
// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
/*
 * Exports the words of a Forge checkout that are NOT defined by a .fth source (core words, intrinsics and the
 * TypeScript primitives the ops package binds into vocabularies) to src/main/resources/builtins/builtins.json.
 * The plugin turns that file into read-only FL declaration files, one per vocabulary, so built-in words resolve
 * and can be navigated to like any other word.
 *
 * Usage:  node tools/export-builtins.mjs <path-to-forge-checkout> [--out <file>] [--check]
 *
 * The checkout must be built (packages/lang/dist, packages/ops/dist exist: `npm run build`). Plain Node ESM,
 * no dependencies. --check compares with the existing file and exits 1 when it is out of date.
 *
 * How TypeScript-defined words are told apart from .fth-defined ones: the loaded runtime's dictionary is walked
 * (every vocabulary, every key of `vocab.words`, private words and aliases included). A WordEntry has a `kind`:
 * "fl", "constant" and "variable" come from a .fth unit (their `meta.source.unit` and `.text` are set);
 * "primitive" and "intrinsic" come from TypeScript (`meta.source` is all null). The script asserts that the two
 * criteria agree (the one exception is the FL prelude of core, kind "fl" without a source unit, exported with kind
 * "prelude"), and drops any primitive that a `.fth` source binds with a PRIMITIVE form (there is none today),
 * because the plugin sees that declaration in the .fth file itself.
 */
import { readFileSync, readdirSync, writeFileSync, existsSync, mkdirSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const flags = new Set(args.filter((a) => a === "--check"));
const outIdx = args.indexOf("--out");
const out = outIdx >= 0 ? resolve(args[outIdx + 1]) : resolve(here, "../src/main/resources/builtins/builtins.json");
const positional = args.filter((a, i) => !a.startsWith("--") && args[i - 1] !== "--out");
if (positional.length !== 1) {
  console.error("usage: node tools/export-builtins.mjs <path-to-forge-checkout> [--out <file>] [--check]");
  process.exit(2);
}
const forge = resolve(positional[0]);
const pkg = (p) => join(forge, "packages", p);
for (const need of [pkg("ops/dist/bundle.js"), pkg("ops/dist/sources-node.js"), pkg("lang/dist/index.js")]) {
  if (!existsSync(need)) {
    console.error(`missing ${need}: build the forge checkout first (npm run build)`);
    process.exit(2);
  }
}

const { assembleRuntime } = await import(pathToFileURL(pkg("ops/dist/bundle.js")).href);
const { loadBundleNode } = await import(pathToFileURL(pkg("ops/dist/sources-node.js")).href);

const runtime = assembleRuntime(await loadBundleNode("all"));
const dict = runtime.dictionary;

// PRIMITIVE forms in .fth sources: `PRIMITIVE name` under the last `IN: vocab`.
const boundByFth = new Set();
const vocabDir = pkg("ops/vocab");
for (const f of readdirSync(vocabDir).filter((n) => n.endsWith(".fth") || n.endsWith(".fl"))) {
  let vocab = "user";
  for (const line of readFileSync(join(vocabDir, f), "utf8").split("\n")) {
    const stripped = line.replace(/^\s*\\.*$/, "");
    let m = /^\s*IN:\s+(\S+)/.exec(stripped);
    if (m) vocab = m[1];
    m = /^\s*PRIMITIVE\s+(\S+)/.exec(stripped);
    if (m) boundByFth.add(`${vocab}:${m[1]}`);
  }
}

const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0); // code unit order is fine: names are ASCII
const words = [];
for (const vocabName of dict.vocabs()) {
  const vocab = dict.vocab(vocabName);
  for (const [key, entry] of vocab.words) {
    const fromTs = entry.kind === "primitive" || entry.kind === "intrinsic";
    const fromFth = entry.meta.source.unit !== null;
    // FL v0.2: the core prelude (LANGUAGE.md section 13.8) is FL code compiled from a string inside
    // packages/lang, so its words have kind "fl" and no source unit. No .fth file declares them.
    const prelude = entry.kind === "fl" && !fromFth && vocabName === "core";
    if (fromTs === fromFth && !prelude) {
      throw new Error(`${vocabName}:${key}: kind ${entry.kind} and source.unit ${entry.meta.source.unit} disagree`);
    }
    if (fromFth) continue;
    if (boundByFth.has(`${vocabName}:${key}`)) continue;
    const meta = entry.meta;
    const w = {
      vocab: vocabName,
      name: key,
      kind: prelude ? "prelude" : entry.kind,
      effect: meta.effect.text,
      doc: meta.doc ?? null,
      category: meta.category ?? null,
    };
    if (key !== entry.name) w.aliasOf = entry.name; // an alias key of a TypeScript word
    if (meta.seeAlso.length) w.seeAlso = [...meta.seeAlso];
    words.push(w);
  }
}
words.sort((a, b) => cmp(a.vocab, b.vocab) || cmp(a.name, b.name));
// The plugin writes one file per vocabulary (<vocab>.fl): names must stay distinct on case-insensitive disks.
const lower = new Map();
for (const w of words) {
  const prev = lower.get(w.vocab.toLowerCase());
  if (prev !== undefined && prev !== w.vocab) throw new Error(`vocabularies ${prev} and ${w.vocab} differ only in case`);
  lower.set(w.vocab.toLowerCase(), w.vocab);
}

const text =
  '{"format":1,"generator":"tools/export-builtins.mjs","words":[\n' +
  words.map((w) => JSON.stringify(w)).join(",\n") +
  "\n]}\n";

if (flags.has("--check")) {
  const current = existsSync(out) ? readFileSync(out, "utf8") : "";
  if (current !== text) {
    console.error(`${out} is out of date; regenerate with: node tools/export-builtins.mjs ${forge}`);
    process.exit(1);
  }
  console.log(`${out} is up to date (${words.length} words)`);
} else {
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, text);
  const per = new Map();
  for (const w of words) per.set(w.vocab, (per.get(w.vocab) ?? 0) + 1);
  const top = [...per].sort((a, b) => b[1] - a[1] || cmp(a[0], b[0])).slice(0, 8);
  console.log(`wrote ${words.length} words in ${per.size} vocabularies to ${out}`);
  console.log(`largest: ${top.map(([v, n]) => `${v}=${n}`).join(" ")}`);
}
