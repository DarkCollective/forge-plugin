# Running and debugging FL from the plugin: feasibility

Status: research, November 2026. Nothing here is implemented in the plugin yet.

## Verdict

**Both are feasible, and the Forge engine already does most of the hard part.** The engine was designed with a
debugger in mind (LANGUAGE.md §10.9): breakpoints by word and instruction, an *async* step hook that can pause a
run, and a source position for every compiled instruction. The plugin (Java) and the engine (TypeScript) talk
through a small Node.js "runner" process over stdin/stdout.

- **Run a word with input**: small. A run configuration, a gutter icon on words and a runner script.
- **Debugger** (line breakpoints, pause, step over/into/out, call stack, data stack): medium. Uses the IntelliJ
  XDebugger API on the plugin side; the engine side exists.
- **Locals in the Variables view**: needs a small addition to `@forge/lang` (the step event does not expose
  frames or locals). Forge's CLAUDE.md freezes the `packages/*` types, so this is an owner decision.

## What a spike showed

A 60-line Node script (`node spike.mjs <forge-checkout>`, against the built checkout, not committed) did all of
this:

```text
assemble all vocabularies: 359 ms
assemble base32 + deps:    33 ms
run base32:encode on "hello" -> "NBSWY3DP" (264 steps)
run the edited word base32:shout -> "FORGE!"            <- a word that exists only in "unsaved" editor text
unit of base32:shout: base32.fth | locals: [ 's', 'u' ]
line 97 of base32.fth -> base32:shout ip 3              <- line breakpoint mapped through CompiledWord.positions
  BREAK base32:shout ip=3 op=local line=97:3 depth=2 stack=[]
  step  base32:shout ip=4 op=lit line=97:5 depth=2 stack=["FORGE"]
  step  base32:shout ip=5 op=prim line=97:9 depth=2 stack=["FORGE" "!"]
  step  base32:shout ip=6 op=ret line=97:13 depth=2 stack=["FORGE!"]
debug run result -> "FORGE!"
```

The `onStep` hook awaited a promise at each pause; that is exactly where the IDE waits for *Resume* or *Step*.

## What Forge provides (and the gaps)

| Need | In Forge today | Source |
|---|---|---|
| Build a runtime with all vocabularies | `assembleRuntime(await loadBundle(...))`, ~360 ms for all, ~30 ms for one vocabulary and its dependencies | `@forge/ops` bundle.ts |
| Run unsaved editor text | `createBundleLoader(provider)` takes any source provider: wrap the Node one and substitute the editor's text for a file | `@forge/ops` bundle.ts, sources-node.ts |
| Run a word with input | `runtime.run(source)` (input as FL literals, then the word) or `runtime.call(name, args)` | LANGUAGE.md §10.13 |
| Result, output, errors | `RunResult`: `ok`, `stack`, `output`, `steps`, `elapsedMs`, `effects`, `error` with `pos` and `frames` | §10.12, §11 |
| Stop button | `RunOptions.signal` / `abortFlag` | §10.7 |
| Breakpoints | `RunOptions.breakpoints: { word, ip? }[]` | §10.9 |
| Pause and step | `onStep` may return a promise (the VM awaits it) or `"stop"`; `stepAll` calls it before every instruction | §10.9 |
| Line ↔ instruction | `CompiledWord.positions[ip]` (line, col, offset) and `WordMeta.source.unit` (the file) | types.ts |
| Data stack at a pause | `StepEvent.stack` (live view) | types.ts |
| Call depth | `StepEvent.returnDepth` (enough for step over / step out) | types.ts |
| Variables (`VARIABLE`) | pass your own `vars` map and read it at a pause | §10.9 |
| **Call stack frames** | **missing**: only the current word and the depth | gap |
| **Local values** (header names, `:>`) | **missing**: names are in `CompiledWord.localNames`, values are not exposed | gap |

The smallest change that closes both gaps is one optional field on `StepEvent`, for example
`frames(): { word, ip, pos, locals: readonly Value[] }[]` (a method, so a step that does not ask costs nothing).
It is additive, but `@forge/lang` types are frozen by the Forge repository's rules, so it needs your go-ahead.
Without it, the debugger can still rebuild the call stack from the stream of step events (word and return depth),
just without local values.

Things the IDE must handle itself:

- **Defaults are metadata only** (§5.4): the VM never fills in a missing argument. The run dialog must push the
  default of every parameter the user leaves empty (the spike's first attempt failed with `E_STACK_UNDERFLOW` for
  exactly this reason).
- **Early binding** (§6.5): a word compiled earlier keeps calling the old definition. Build a **fresh runtime
  per run** from the current sources rather than reloading one file into a long-lived runtime (30 to 400 ms).
- **Secrets**: `key`-typed values show as `<redacted>` in snapshots and traces (§10.14). That is the right
  default for a debugger too.

## Proposed architecture

```text
IntelliJ (Java)                                   Node.js (TypeScript, ships with the plugin)
-----------------                                 -------------------------------------------
Run configuration "FL word"  ----- spawn ------>  forge-runner.mjs <forge checkout>
  word, input, limits                             imports @forge/ops dist from the checkout
Console, result view   <------ JSON lines ------  {"event":"result", stack, output, steps, error}
XDebugProcess          <------ JSON lines ----->  onStep: {"event":"paused", word, pos, stack, depth}
  breakpoints, resume, step                         waits for {"cmd":"resume"|"step"|"stepOver"|...}
```

**Why a Node process and not JavaScript inside the JVM:** Forge is ESM that loads its primitive modules with
dynamic `import()` and depends on npm packages (noble ciphers and hashes, css-select, ...). Running that in
GraalJS inside the IDE means a large dependency, a separate npm resolution story and slower crypto. A child
process uses the exact engine the web app uses. Cost: the user needs Node 20 or newer and a **built** Forge
checkout. The plugin already depends on one for `tools/export-builtins.mjs`. Later, the runner and `@forge/ops`
could be bundled into one file shipped inside the plugin, so only Node is needed.

### Run (phase 1)

- A `RunLineMarkerContributor` puts a ▶ on every public word (and `MEMO:` word); it opens a run configuration
  pre-filled with the word.
- An input dialog generated from the header: a field per input with its type, constraint and default; a combo box
  for `choice{…}` and `choice<…>`; text, hex or Base64 for `bytes`/`key`/`toggle`.
- The runner builds the runtime (the edited, unsaved files substituted), pushes the inputs and defaults, runs the
  word, and prints the result stack, `output`, steps, time and effects. An error links to its file and line.
- Settings: the path of the Forge checkout and of `node`, plus limits (`maxSteps`, `maxMillis`).

### Debug (phase 2)

- An `XLineBreakpointType` for FL files. On start, the runner maps each `(file, line)` to `(word, ip)` pairs from
  `CompiledWord.positions` of the words whose `source.unit` is that file.
- Pause: `onStep` sends a `paused` event and awaits the next command. To keep a run fast, the hook returns
  synchronously (no promise) on every instruction it does not stop at.
- Step into: the next instruction with a new source line. Step over: the next line at the same or a smaller
  `returnDepth`. Step out: the first instruction at a smaller `returnDepth`.
- Frames view: from the shadow call stack (or the `frames()` addition). Variables view: the data stack (top first),
  `VARIABLE`s from the `vars` map, and locals once the engine exposes them.
- Optional extra: run with `trace: true` and show the trace in a tool window, a "time travel" view that needs no
  pausing at all.

### An alternative worth considering

Write the runner as a **Debug Adapter Protocol** server in TypeScript. VS Code would then get FL debugging for
free, and IntelliJ could use it through the LSP4IJ plugin's DAP client. The trade-off is a dependency on a
third-party plugin and a less native feel in IntelliJ. If IntelliJ is the only IDE that matters, a native
XDebugger client over a small JSON protocol is simpler.

## Effort (rough)

| Piece | Estimate |
|---|---|
| Runner script, run configuration, gutter icon, console output | 3 to 5 days |
| Input dialog generated from headers, defaults, choice types | 2 to 3 days |
| Debugger: breakpoints, pause, resume, stepping, frames from depth, data stack | 1.5 to 2 weeks |
| Locals in the Variables view | 2 to 3 days, after the `@forge/lang` addition |
| Bundling the runner so only Node is needed | 1 to 2 days |

## Risks

- **Version skew** between the runner and a Forge checkout: the runner should check the language version at
  start and refuse a checkout it does not know.
- **Node availability** on the developer's machine; detect it and explain in the settings page.
- **`+async` primitives** (today one module, `jsonata`) suspend inside the VM; pausing and stopping still work
  through the same hook and the abort signal.
- **Stepping speed**: `stepAll` calls into JavaScript per instruction; synchronous returns keep it cheap, but
  stepping through a hash of a large input will be slow. Breakpoints on entry (no `stepAll`) cost nothing until hit.
