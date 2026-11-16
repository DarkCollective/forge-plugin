// Copyright 2026 Darkcollective, LLC. Licensed under the Apache License, Version 2.0.
//
// Runs the op test vectors of a built Forge checkout (RECIPES.md section 9: packages/ops/vocab/<vocab>.test.json)
// and reports them as IntelliJ test events (TeamCity service messages). Started by the IntelliJ plugin's "FL Vectors"
// run configuration; plain Node ESM (Node 20+), no dependencies of its own.
//
// Request: all of stdin, one JSON object
//   { "checkout": "/path/to/forge",               a checkout where `npm run build` has run
//     "files":    ["base32.test.json"],          optional: these files of packages/ops/vocab; default all
//     "entries":  { "base32.test.json": [0, 3] }, optional: only these entries (array indices) of a file
//     "op":       "base32:encode" }               optional: only the entries of this op (an alias works too)
//
// The verdict of an op vector is Forge's own: runOpVectors of @forge/worker, the harness of the Vitest suite, on one
// vector at a time. On a failure the vector is baked once more to show the expected and the actual value. A check
// pair (an entry with "checks") is evaluated as packages/ops/src/checks-pairs.test.ts does, with evaluateChecks of
// @forge/ops/checks on the op's checks of that scope. When a whole file runs (no "entries", no "op"), a last test
// "coverage" applies the CI rules to the file's vocabulary: every op has a vector, an op with input checks has a
// pass and a fail input pair, an op with output checks a pass output pair.
//
// Tree: one suite per file (the vocabulary), one suite per op, one test per entry ("#<index> <name>").
// Locations: flvectors://<file> (a file), flop://<vocab:op> (an op), flvector://<file>#<index> (an entry).
// Exit code: 0 all passed, 1 a test failed, 2 a setup problem (no build, bad request).

import { existsSync, readFileSync, readdirSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

const SUFFIX = ".test.json";

function fail(message) {
  process.stderr.write(message + "\n");
  process.exitCode = 2;
}

/** A TeamCity service message attribute value. */
function tc(value) {
  return String(value).replace(/[|'\n\r[\]\u0085\u2028\u2029]/g, (c) => ({
    "|": "||", "'": "|'", "\n": "|n", "\r": "|r", "[": "|[", "]": "|]",
    "\u0085": "|x", "\u2028": "|l", "\u2029": "|p",
  })[c]);
}

function message(name, attrs) {
  const parts = Object.entries(attrs)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${k}='${tc(v)}'`);
  process.stdout.write(`##teamcity[${name} ${parts.join(" ")}]\n`);
}

async function readStdin() {
  const chunks = [];
  for await (const c of process.stdin) chunks.push(c);
  return Buffer.concat(chunks).toString("utf8");
}

const hex = (bytes) => Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");

/** Text when the bytes are UTF-8 without control characters other than line breaks and tabs, otherwise null. */
function textOf(bytes) {
  try {
    const text = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    // eslint-disable-next-line no-control-regex
    return /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f]/.test(text) ? null : text;
  } catch {
    return null;
  }
}

/** Expected and actual bytes as two comparable strings: both as text when both are text, else both as hex. */
function showPair(want, got) {
  const a = textOf(want);
  const b = textOf(got);
  if (a !== null && b !== null) return { expected: a, actual: b };
  return { expected: `hex ${hex(want)}`, actual: `hex ${hex(got)}` };
}

async function main() {
  let request;
  try {
    request = JSON.parse(await readStdin());
  } catch (e) {
    return fail(`forge-vectors: the request is not JSON: ${e.message}`);
  }
  const { checkout, files = [], entries = {}, op = null } = request;
  if (typeof checkout !== "string" || checkout === "") return fail("forge-vectors: no Forge checkout given");

  const pkg = (name, file) => join(checkout, "packages", name, "dist", file);
  const needed = [pkg("worker", "index.js"), pkg("ops", "index.js"), pkg("ops", "sources-node.js"),
    pkg("ops", join("checks", "index.js")), pkg("recipe", "index.js")];
  for (const file of needed) {
    if (!existsSync(file)) {
      return fail(`forge-vectors: ${file} is missing. Is ${checkout} a Forge checkout, and has 'npm run build' run there?`);
    }
  }
  const load = (file) => import(pathToFileURL(file).href);
  const [worker, ops, opsNode, checks, recipe] = await Promise.all(needed.map(load));

  const vocabDir = join(checkout, "packages", "ops", "vocab");
  const names = files.length > 0
    ? files
    : readdirSync(vocabDir).filter((f) => f.endsWith(SUFFIX)).sort();

  // The catalogue (ops and their aliases) and the runtime for the checks of each op: from the files on disk.
  const bundle = await opsNode.loadBundleNode();
  const runtime = ops.assembleRuntime(bundle);
  const catalogue = ops.buildCatalogue(runtime);
  const canonical = new Map();
  for (const o of catalogue.ops) {
    canonical.set(o.qname, o.qname);
    const vocab = o.qname.slice(0, o.qname.indexOf(":"));
    for (const alias of o.aliases) if (!canonical.has(`${vocab}:${alias}`)) canonical.set(`${vocab}:${alias}`, o.qname);
  }
  const canon = (name) => canonical.get(name) ?? name;
  const checksOf = (qname, scope) => {
    try {
      const entry = runtime.lookup(qname, { current: qname.slice(0, qname.indexOf(":")), using: [] });
      return (entry.meta?.discovery?.checks ?? []).filter((c) => c.scope === scope);
    } catch {
      return [];
    }
  };

  const baker = worker.createInProcessBaker({ loadBundle: opsNode.loadBundleNode });
  await baker.init({ vocabs: "all" });

  // Read and select.
  const wantOp = typeof op === "string" && op !== "" ? canon(op) : null;
  const plan = [];
  let count = 0;
  for (const file of names) {
    let list;
    let loadError = null;
    try {
      list = JSON.parse(readFileSync(join(vocabDir, file), "utf8"));
      if (!Array.isArray(list)) throw new Error("the file is not a JSON array");
    } catch (e) {
      loadError = e.message;
      list = [];
    }
    const only = Array.isArray(entries[file]) ? new Set(entries[file]) : null;
    const selected = [];
    list.forEach((entry, index) => {
      if (only !== null && !only.has(index)) return;
      if (wantOp !== null && canon(entry?.op) !== wantOp) return;
      selected.push({ entry, index });
    });
    const whole = only === null && wantOp === null && loadError === null;
    plan.push({ file, list, selected, whole, loadError });
    count += selected.length + (whole || loadError !== null ? 1 : 0);
  }
  message("testCount", { count });

  let failed = false;
  const test = async (name, location, body) => {
    const started = Date.now();
    message("testStarted", { name, locationHint: location, captureStandardOutput: "false" });
    let problem;
    try {
      problem = await body();
    } catch (e) {
      problem = { message: `the runner failed: ${e?.stack ?? e}` };
    }
    if (problem) {
      failed = true;
      const comparison = problem.expected !== undefined && problem.actual !== undefined;
      message("testFailed", {
        name,
        message: problem.message,
        details: problem.details,
        ...(comparison ? { type: "comparisonFailure", expected: problem.expected, actual: problem.actual } : {}),
      });
    }
    message("testFinished", { name, duration: Date.now() - started });
  };

  for (const { file, list, selected, whole, loadError } of plan) {
    const vocab = file.slice(0, -SUFFIX.length);
    message("testSuiteStarted", { name: vocab, locationHint: `flvectors://${file}` });
    if (loadError !== null) {
      await test("load", `flvectors://${file}`, async () => ({ message: `${file} cannot be read: ${loadError}` }));
    }
    // One suite per op, in the order the ops first appear in the file.
    const byOp = new Map();
    for (const s of selected) {
      const key = typeof s.entry?.op === "string" ? canon(s.entry.op) : "(no op)";
      if (!byOp.has(key)) byOp.set(key, []);
      byOp.get(key).push(s);
    }
    for (const [qname, group] of byOp) {
      message("testSuiteStarted", { name: qname, locationHint: `flop://${qname}` });
      for (const { entry, index } of group) {
        const isPair = entry !== null && typeof entry === "object" && "checks" in entry;
        const label = isPair
          ? `#${index} ${entry.checks} pair${entry.name ? ` "${entry.name}"` : ""} expects ${entry.expect}`
          : `#${index}${entry?.name ? ` ${entry.name}` : ""}`;
        await test(label, `flvector://${file}#${index}`, () =>
          isPair ? checkPair(entry) : vector(file, entry));
      }
      message("testSuiteFinished", { name: qname });
    }
    if (whole) await test("coverage", `flvectors://${file}`, async () => coverage(vocab, list));
    message("testSuiteFinished", { name: vocab });
  }
  process.exitCode = failed ? 1 : 0;

  /** An op vector: Forge's verdict, then the values for a diff. */
  async function vector(file, entry) {
    const report = await worker.runOpVectors({ baker, catalogue, files: { [file]: [entry] } });
    const failure = report.failures[0];
    if (failure === undefined) return null;
    // "<file>#0 <label>: <reason>": the reason.
    const reason = failure.message.slice(`${failure.file}#${failure.index} ${failure.name}: `.length);
    let shown = {};
    try {
      shown = await values(entry);
    } catch {
      // no diff then
    }
    return { message: reason, ...shown };
  }

  /** The expected and the actual value of a failed vector, comparable as text. */
  async function values(entry) {
    const expect = entry.expect;
    let input;
    try {
      input = recipe.decodeEncoded(entry.input);
    } catch {
      return {};
    }
    const result = await baker.bake(
      { forge: 1, steps: [{ kind: "op", op: entry.op, params: entry.params }] },
      input,
      { trust: "trusted", pins: "ignore" },
    );
    const json = (v) => JSON.stringify(v, null, 2);
    const actualText = () =>
      result.ok ? json(worker.outputToJson(result.output))
        : `error ${result.error.code} at ${json(result.error.path)}: ${result.error.message}`;
    if ("error" in expect) {
      const path = expect.error === "E_RECIPE_INVALID" ? null : "/steps/0";
      return {
        expected: `error ${expect.error} at ${json(path)}`,
        actual: result.ok ? `a result: ${actualText()}` : `error ${result.error.code} at ${json(result.error.path)}`,
        details: result.ok ? undefined : result.error.message,
      };
    }
    if ("json" in expect) return { expected: json(expect.json), actual: actualText() };
    const want = recipe.decodeEncoded(expect);
    if (!result.ok) return { expected: textOf(want) ?? `hex ${hex(want)}`, actual: actualText() };
    const out = result.output;
    if (out.kind === "bytes") return showPair(want, out.bytes);
    if (out.kind === "str") return showPair(want, new TextEncoder().encode(out.text));
    return { expected: textOf(want) ?? `hex ${hex(want)}`, actual: `a ${out.kind} output: ${actualText()}` };
  }

  /** A check pair: the op's checks of that scope on the data give the expected result. */
  async function checkPair(entry) {
    const qname = canonical.get(entry.op);
    if (qname === undefined) return { message: `${entry.op} is not an operation of the catalogue` };
    const list = checksOf(qname, entry.checks);
    if (list.length === 0) return { message: `${qname} has no ${entry.checks} checks` };
    let bytes;
    try {
      bytes = recipe.decodeEncoded(entry.data);
    } catch (e) {
      return { message: `bad data: ${e.message}` };
    }
    const got = checks.evaluateChecks(list, checks.dataView(bytes), entry.checks);
    if (got.result === entry.expect) return null;
    const failedCheck = got.failed === undefined ? "" : ` (failing check: ${JSON.stringify(got.failed.test)})`;
    return { message: `the checks gave ${got.result}${failedCheck}`, expected: entry.expect, actual: got.result };
  }

  /** The CI coverage rules for the ops of one vocabulary, over its own file. */
  function coverage(vocab, list) {
    const covered = new Set();
    const pairs = new Map(); // "qname scope" -> expectations
    for (const entry of list) {
      if (entry === null || typeof entry !== "object" || typeof entry.op !== "string") continue;
      const q = canon(entry.op);
      if ("checks" in entry) {
        const key = `${q} ${entry.checks}`;
        pairs.set(key, (pairs.get(key) ?? new Set()).add(entry.expect));
      } else {
        covered.add(q);
      }
    }
    const problems = [];
    for (const o of catalogue.ops) {
      if (!o.qname.startsWith(`${vocab}:`)) continue;
      if (!covered.has(o.qname)) problems.push(`${o.qname} has no vector`);
      const got = (scope) => pairs.get(`${o.qname} ${scope}`) ?? new Set();
      if (checksOf(o.qname, "input").length > 0) {
        for (const want of ["pass", "fail"]) {
          if (!got("input").has(want)) problems.push(`${o.qname} has input checks but no ${want} input pair`);
        }
      }
      if (checksOf(o.qname, "output").length > 0 && !got("output").has("pass")) {
        problems.push(`${o.qname} has output checks but no pass output pair`);
      }
    }
    return problems.length === 0 ? null : { message: problems.join("\n") };
  }
}

try {
  await main();
} catch (e) {
  process.stderr.write(`${e?.stack ?? e}\n`);
  process.exitCode = 2;
}
