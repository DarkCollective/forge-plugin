// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
//
// Runs and debugs FL code with the Forge engine of a built Forge checkout. Started by the IntelliJ plugin's "FL"
// run configuration; plain Node ESM (Node 20+), no dependencies of its own.
//
// Request: the first line of stdin, one JSON object
//   { "checkout": "/path/to/forge",      a checkout where `npm run build` has run
//     "program":  "\"hello\" base32:encode",  console source (docs/LANGUAGE.md section 9)
//     "vocab":    "base32",               optional: current vocabulary while the program runs (private words)
//     "units":    [{ "name": "x.fth", "path": "/abs/x.fth" }],  optional: extra user units, loaded last
//     "limits":   { "maxSteps": 1000000, "maxMillis": 10000 },  optional
//     "debug":    true }                  optional: debug mode, below
//
// Output: the program's printed output, then the result stack (deepest first), then a summary line, all on
// stdout. An FL error goes to stderr with one "at word (file.fth:line:col)" line per frame.
// Exit code: 0 the run succeeded, 1 an FL error (load, compile or run), 2 a setup problem (no build, bad request).
//
// Debug mode. After the runtime is built the runner sends `ready` and waits for `start`. Commands are further
// lines of stdin, one JSON object each:
//   {"cmd":"start","breakpoints":[{"unit":"base32.fth","line":97}]}   run, with these line breakpoints
//   {"cmd":"setBreakpoints","breakpoints":[...]}                     replace the breakpoints (any time)
//   {"cmd":"resume"} {"cmd":"stepOver"} {"cmd":"stepInto"} {"cmd":"stepOut"}   while paused
//   {"cmd":"pause"}                                                  while running
//   {"cmd":"stop"}                                                   end the run (E_ABORTED)
// Events are stdout lines that start with the marker below, followed by one JSON object:
//   {"event":"ready"}
//   {"event":"paused","reason":"breakpoint"|"step"|"pause","frames":[...],"stack":[...]}
//     frames innermost first: {"word","kind":"word"|"quot","unit"|null,"line"|null,"col"|null,"locals":[...]}
//     locals and stack entries: {"name"?,"type","value"} with the value as its repr (secrets redacted);
//     "stack" is deepest first. "locals" is empty when the engine has no StepEvent.frames().
//   {"event":"resumed"}
// A step stops only in code that has a source unit (not in <console> code or the core prelude).

import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { createInterface } from "node:readline";
import { pathToFileURL } from "node:url";

const PROGRAM_UNIT = "<program>";
const MARKER = "\u0001FL-DEBUG ";
const MAX_REPR = 1000;

/** Ends the run with an exit code; caught at the top. stdout to a pipe is asynchronous on macOS, so the process
 * ends by itself (exitCode) rather than by process.exit, which could cut off output still being written. */
class Exit {
  constructor(code) {
    this.code = code;
  }
}

function exit(code) {
  throw new Exit(code);
}

function fail(message) {
  process.stderr.write(message + "\n");
  exit(2);
}

// stdin: the first line is the request; in debug mode the following lines are commands.
const lines = createInterface({ input: process.stdin, crlfDelay: Infinity });
const queue = [];
let waiting = null;
lines.on("line", (line) => {
  if (line.trim() === "") return;
  if (waiting !== null) {
    const w = waiting;
    waiting = null;
    w(line);
  } else {
    queue.push(line);
  }
});
lines.on("close", () => {
  if (waiting !== null) {
    const w = waiting;
    waiting = null;
    w(null);
  }
});
function nextLine() {
  if (queue.length > 0) return Promise.resolve(queue.shift());
  return new Promise((resolve) => (waiting = resolve));
}

async function main() {
  let request;
  try {
    const first = await nextLine();
    request = JSON.parse(first ?? "");
  } catch (e) {
    fail(`forge-runner: the request is not JSON: ${e.message}`);
  }
  const { checkout, program, vocab, units = [], limits = {}, debug = false } = request;
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
  const { isForgeError, repr, reprShort, redactValue } = lang;

  const extra = [];
  for (const u of units) {
    try {
      extra.push({ name: u.name, source: readFileSync(u.path, "utf8") });
    } catch (e) {
      fail(`forge-runner: cannot read ${u.path}: ${e.message}`);
    }
  }

  const show = (v) => repr(redactValue(v));
  const showShort = (v) => (reprShort ?? ((x) => repr(x)))(redactValue(v), MAX_REPR);

  /** The entry of a qualified name, looked up from its own vocabulary so that private words are found; or null. */
  function entryOf(runtime, qname) {
    if (qname.startsWith("<")) return null;
    const colon = qname.indexOf(":");
    try {
      return colon > 0
        ? runtime.lookup(qname, { current: qname.slice(0, colon), using: [] })
        : runtime.lookup(qname);
    } catch {
      return null;
    }
  }

  /** "file:line:col" of instruction `ip` of word `qname`, or null. */
  function where(runtime, qname, ip) {
    const entry = entryOf(runtime, qname);
    const unit = entry?.meta?.source?.unit;
    const pos = entry?.kind === "fl" && entry.code !== null ? entry.code.positions[ip] : null;
    if (unit == null) return null;
    return pos == null ? unit : `${unit}:${pos.line}:${pos.col}`;
  }

  function reportError(err, runtime, extraUnit) {
    if (!isForgeError(err) && err?.name !== "ForgeError") {
      process.stderr.write(`${err?.stack ?? err}\n`);
      return;
    }
    const out = [`${err.code}: ${err.message}`];
    // A load error has no frames: its position is in the unit named at the start of the message.
    if (extraUnit !== undefined && err.pos != null) out.push(`  at ${extraUnit}:${err.pos.line}:${err.pos.col}`);
    for (const frame of err.frames ?? []) {
      const loc = frame.word === "<console>" ? null : where(runtime, frame.word, frame.ip);
      out.push(`  at ${frame.word}${loc === null ? "" : ` (${loc})`}`);
    }
    if ((err.frames ?? []).length === 0 && extraUnit === undefined && err.pos != null) {
      out.push(`  at ${PROGRAM_UNIT}:${err.pos.line}:${err.pos.col}`);
    }
    if (err.framesSkipped > 0) out.push(`  (${err.framesSkipped} frames skipped)`);
    if (err.stackSnapshot?.length > 0) out.push(`stack: ${err.stackSnapshot.map(show).join(" ")}`);
    process.stderr.write(out.join("\n") + "\n");
  }

  let runtime;
  try {
    const bundle = await opsNode.loadBundleNode();
    runtime = ops.assembleRuntime(bundle, { units: extra });
  } catch (e) {
    // assembleRuntime prefixes the unit name: "base32.fth: message"
    const unit = isForgeError(e) ? /^([^:\s]+\.(?:fth|fl|forge)):/.exec(e.message)?.[1] : undefined;
    reportError(e, undefined, unit);
    exit(1);
  }

  const options = { limits };
  if (typeof vocab === "string" && vocab !== "") options.scope = { current: vocab, using: [] };

  // ---- debugging ------------------------------------------------------------------------------------------------

  function send(event) {
    process.stdout.write(MARKER + JSON.stringify(event) + "\n");
  }

  function parseCommand(line) {
    try {
      return JSON.parse(line);
    } catch {
      return null;
    }
  }

  /** Debug state: line breakpoints as "unit:line", the step mode and the location each step started from. */
  const dbg = {
    breakpoints: new Set(),
    mode: "run", // run | over | into | out
    anchorDepth: 0,
    pausedDepth: 0,
    pauseRequested: false,
    stopRequested: false,
    /** Per activation depth, the line last executed there: {word, line, ip}. */
    lineAt: [],
    units: new Map(),
  };

  function setBreakpoints(list) {
    dbg.breakpoints = new Set((list ?? []).map((b) => `${b.unit}:${b.line}`));
  }

  function unitOf(qname) {
    let u = dbg.units.get(qname);
    if (u === undefined) {
      u = entryOf(runtime, qname)?.meta?.source?.unit ?? null;
      dbg.units.set(qname, u);
    }
    return u;
  }

  /** Commands that may arrive while the program runs (the VM yields to the event loop regularly). */
  function onRunningCommand(cmd) {
    if (cmd === null) return;
    if (cmd.cmd === "setBreakpoints") setBreakpoints(cmd.breakpoints);
    else if (cmd.cmd === "pause") dbg.pauseRequested = true;
    else if (cmd.cmd === "stop") dbg.stopRequested = true;
  }

  function frameInfo(f) {
    const unit = unitOf(f.word);
    return {
      word: f.word,
      kind: f.kind ?? "word",
      unit,
      line: f.pos?.line ?? null,
      col: f.pos?.col ?? null,
      locals: (f.locals ?? []).map((l) => ({ name: l.name, type: l.value?.tag ?? "?", value: showShort(l.value) })),
    };
  }

  async function pauseAt(ev, reason) {
    const frames = typeof ev.frames === "function"
      ? ev.frames().map(frameInfo)
      : [frameInfo({ word: ev.word, kind: "word", pos: ev.pos, locals: [] })];
    const stack = Array.from(ev.stack, (v) => ({ type: v.tag, value: showShort(v) }));
    dbg.pauseRequested = false;
    send({ event: "paused", reason, frames, stack });
    for (;;) {
      const line = await nextLine();
      if (line === null) return "stop";
      const cmd = parseCommand(line);
      if (cmd === null) continue;
      switch (cmd.cmd) {
        case "setBreakpoints":
          setBreakpoints(cmd.breakpoints);
          continue;
        case "resume":
        case "stepOver":
        case "stepInto":
        case "stepOut":
          dbg.mode = { resume: "run", stepOver: "over", stepInto: "into", stepOut: "out" }[cmd.cmd];
          dbg.anchorDepth = dbg.pausedDepth;
          send({ event: "resumed" });
          listenWhileRunning();
          return undefined;
        case "stop":
          return "stop";
        default:
          continue;
      }
    }
  }

  let listening = false;
  /** While the program runs, read commands as they come (pause, stop, breakpoints). */
  function listenWhileRunning() {
    if (listening) return;
    listening = true;
    const pump = () => {
      if (queue.length > 0) {
        while (queue.length > 0) onRunningCommand(parseCommand(queue.shift()));
      }
      if (!listening) return;
      waiting = (line) => {
        if (!listening) {
          queue.unshift(line);
          return;
        }
        onRunningCommand(line === null ? { cmd: "stop" } : parseCommand(line));
        pump();
      };
    };
    pump();
  }

  function stopListening() {
    listening = false;
    if (waiting !== null) {
      // Hand the pending reader back: the next line goes to the queue.
      waiting = null;
    }
  }

  /**
   * Activation tracking. `returnDepth` also counts bookkeeping frames (list literals, loops, try, marks), so the
   * runner keeps its own stack of activations: the return depth at which each started. A deeper event is a new
   * activation when control jumped there (a call or the start of a quotation), not when it ran on to the next
   * instruction of the same word (a list or loop mark was pushed). A shallower event ends the activations above it.
   */
  const acts = [];
  let prevEv = null;

  function activationDepth(ev) {
    const depth = ev.returnDepth;
    while (acts.length > 0 && acts[acts.length - 1] > depth) acts.pop();
    const jumped = prevEv === null || prevEv.word !== ev.word || ev.ip !== prevEv.ip + 1;
    if (acts.length === 0 || (depth > prevEv.depth && jumped)) acts.push(depth);
    prevEv = { word: ev.word, ip: ev.ip, depth };
    return acts.length;
  }

  function hook(ev) {
    if (dbg.stopRequested) return "stop";
    const depth = activationDepth(ev);
    const line = ev.pos?.line ?? null;
    // Has this activation just entered a line? A jump back in the same activation (the next pass of a loop or of
    // a quotation run by a combinator) enters the line again.
    dbg.lineAt.length = Math.min(dbg.lineAt.length, depth + 1);
    const last = dbg.lineAt[depth];
    const entered = last === undefined || last.word !== ev.word || last.line !== line || ev.ip <= last.ip;
    dbg.lineAt[depth] = { word: ev.word, line, ip: ev.ip };

    const unit = unitOf(ev.word);
    if (unit === null || line === null) return undefined;
    let reason = null;
    if (dbg.pauseRequested) {
      reason = "pause";
    } else if (entered && dbg.breakpoints.has(`${unit}:${line}`)) {
      reason = "breakpoint";
    } else if (dbg.mode === "into" && entered) {
      reason = "step";
    } else if (dbg.mode === "over" && (depth < dbg.anchorDepth || (depth === dbg.anchorDepth && entered))) {
      reason = "step";
    } else if (dbg.mode === "out" && depth < dbg.anchorDepth) {
      reason = "step";
    }
    if (reason === null) return undefined;
    stopListening();
    dbg.pausedDepth = depth;
    return pauseAt(ev, reason);
  }

  if (debug) {
    send({ event: "ready" });
    for (;;) {
      const line = await nextLine();
      if (line === null) exit(0);
      const cmd = parseCommand(line);
      if (cmd?.cmd === "start") {
        setBreakpoints(cmd.breakpoints);
        break;
      }
      if (cmd?.cmd === "stop") exit(0);
    }
    options.onStep = hook;
    options.stepAll = true;
    options.yieldEvery = 1024; // let pause and stop commands in while the program runs
    listenWhileRunning();
  }

  // ---- the run ----------------------------------------------------------------------------------------------------

  process.stdout.write(`> ${program.trim().replace(/\s+/g, " ")}\n`);
  let result;
  try {
    result = await runtime.run(program, options);
  } catch (e) {
    reportError(e, runtime);
    exit(1);
  }
  stopListening();
  lines.close();

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
    exit(0);
  }
  reportError(result.error, runtime);
  process.stdout.write(`-- failed after ${summary}\n`);
  exit(1);
}

try {
  await main();
} catch (e) {
  if (!(e instanceof Exit)) {
    process.stderr.write(`${e?.stack ?? e}\n`);
    process.exitCode = 2;
  } else {
    process.exitCode = e.code;
  }
}
lines.close();
process.stdin.destroy();
