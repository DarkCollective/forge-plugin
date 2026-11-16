// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
//
// Runs FL code with the Forge engine of a built Forge checkout. Started by the IntelliJ plugin's "FL" run
// configuration; plain Node ESM (Node 20+), no dependencies of its own.
//
// Request: one JSON object on stdin
//   { "checkout": "/path/to/forge",      a checkout where `npm run build` has run
//     "program":  "\"hello\" base32:encode",  console source (docs/LANGUAGE.md section 9)
//     "vocab":    "base32",               optional: current vocabulary while the program runs (private words)
//     "units":    [{ "name": "x.fth", "path": "/abs/x.fth" }],  optional: extra user units, loaded last
//     "limits":   { "maxSteps": 1000000, "maxMillis": 10000 } }  optional
//
// Output: the program's printed output, then the result stack (deepest first), then a summary line, all on
// stdout. An FL error goes to stderr with one "at word (file.fth:line:col)" line per frame.
// Exit code: 0 the run succeeded, 1 an FL error (load, compile or run), 2 a setup problem (no build, bad request).

import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const PROGRAM_UNIT = "<program>";

function fail(message) {
  process.stderr.write(message + "\n");
  process.exit(2);
}

async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return Buffer.concat(chunks).toString("utf8");
}

let request;
try {
  request = JSON.parse(await readStdin());
} catch (e) {
  fail(`forge-runner: the request is not JSON: ${e.message}`);
}
const { checkout, program, vocab, units = [], limits = {} } = request;
if (typeof checkout !== "string" || checkout === "") fail("forge-runner: no Forge checkout given");
if (typeof program !== "string" || program.trim() === "") fail("forge-runner: the program is empty");

const opsDist = join(checkout, "packages", "ops", "dist");
const langDist = join(checkout, "packages", "lang", "dist");
for (const file of [join(opsDist, "index.js"), join(opsDist, "sources-node.js"), join(langDist, "index.js")]) {
  if (!existsSync(file)) {
    fail(`forge-runner: ${file} is missing. Is ${checkout} a Forge checkout, and has 'npm run build' run there?`);
  }
}

const ops = await import(pathToFileURL(join(opsDist, "index.js")).href);
const opsNode = await import(pathToFileURL(join(opsDist, "sources-node.js")).href);
const lang = await import(pathToFileURL(join(langDist, "index.js")).href);
const { isForgeError, repr, redactValue } = lang;

const extra = [];
for (const u of units) {
  try {
    extra.push({ name: u.name, source: readFileSync(u.path, "utf8") });
  } catch (e) {
    fail(`forge-runner: cannot read ${u.path}: ${e.message}`);
  }
}

const show = (v) => repr(redactValue(v));

/** "file:line:col" of instruction `ip` of word `qname`, or null. */
function where(runtime, qname, ip) {
  if (qname.startsWith("<")) return null;
  let entry;
  try {
    entry = runtime.lookup(qname);
  } catch {
    return null;
  }
  const unit = entry.meta?.source?.unit;
  const pos = entry.kind === "fl" && entry.code !== null ? entry.code.positions[ip] : null;
  if (unit == null) return null;
  return pos == null ? unit : `${unit}:${pos.line}:${pos.col}`;
}

function reportError(err, runtime, extraUnit) {
  if (!isForgeError(err) && err?.name !== "ForgeError") {
    process.stderr.write(`${err?.stack ?? err}\n`);
    return;
  }
  const lines = [`${err.code}: ${err.message}`];
  // A load error has no frames: its position is in the unit named at the start of the message.
  if (extraUnit !== undefined && err.pos != null) lines.push(`  at ${extraUnit}:${err.pos.line}:${err.pos.col}`);
  for (const frame of err.frames ?? []) {
    const loc = frame.word === "<console>" ? null : where(runtime, frame.word, frame.ip);
    lines.push(`  at ${frame.word}${loc === null ? "" : ` (${loc})`}`);
  }
  if ((err.frames ?? []).length === 0 && extraUnit === undefined && err.pos != null) {
    lines.push(`  at ${PROGRAM_UNIT}:${err.pos.line}:${err.pos.col}`);
  }
  if (err.framesSkipped > 0) lines.push(`  (${err.framesSkipped} frames skipped)`);
  if (err.stackSnapshot?.length > 0) lines.push(`stack: ${err.stackSnapshot.map(show).join(" ")}`);
  process.stderr.write(lines.join("\n") + "\n");
}

let runtime;
try {
  const bundle = await opsNode.loadBundleNode();
  runtime = ops.assembleRuntime(bundle, { units: extra });
} catch (e) {
  // assembleRuntime prefixes the unit name: "base32.fth: message"
  const unit = isForgeError(e) ? /^([^:\s]+\.(?:fth|fl|forge)):/.exec(e.message)?.[1] : undefined;
  reportError(e, undefined, unit);
  process.exit(1);
}

const options = { limits };
if (typeof vocab === "string" && vocab !== "") options.scope = { current: vocab, using: [] };

process.stdout.write(`> ${program.trim().replace(/\s+/g, " ")}\n`);
let result;
try {
  result = await runtime.run(program, options);
} catch (e) {
  reportError(e, runtime);
  process.exit(1);
}

if (result.output !== "") process.stdout.write(result.output.endsWith("\n") ? result.output : result.output + "\n");
const summary =
  `${result.steps} steps, ${result.elapsedMs.toFixed(1)} ms` +
  (result.effects.length > 0 ? `, effects: ${result.effects.join(" ")}` : "");
if (result.ok) {
  if (result.stack.length === 0) {
    process.stdout.write("stack: (empty)\n");
  } else {
    process.stdout.write(`stack (${result.stack.length}, deepest first):\n`);
    for (const v of result.stack) process.stdout.write(`  ${show(v)}\n`);
  }
  process.stdout.write(`-- ${summary}\n`);
  process.exit(0);
}
reportError(result.error, runtime);
process.stdout.write(`-- failed after ${summary}\n`);
process.exit(1);
