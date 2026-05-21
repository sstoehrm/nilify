# drill v1 — Design

## 1. Overview & scope

drill is a Clojure/babashka library that turns small declarative specifications
into committed Clojure source files, by delegating implementation to Claude
Code as a subagent. The author writes specs as plain Clojure data in their
application file. drill runs in one of two modes:

- **Run mode** (default): each `drill/feature` call returns the matching
  generated implementation; the user's `main-fn` runs.
- **Generate mode** (`--generate`): each `drill/feature` call registers its
  spec; drill then shells out to Claude Code to produce missing or stale
  implementations and writes them to `drill_generated/<id>.clj`.

### In v1

- Two primitives: `feature` (codegen-once) and `produce` (runtime LLM call).
- `:lang :babashka` (default) and `:lang :clojure` for `feature`.
- LLM invocation via the `claude` CLI for both code generation and `produce`
  runtime calls (single integration path).
- REPL-driven generation, regeneration, drift detection.
- A shipped Claude Code skill (`skills/drill-feature-author/`) that teaches
  Claude how to write drill-shaped implementations.
- Per-primitive Malli schemas. drill validates inputs before dispatch and
  outputs after.

### Deferred

- `system` and `api` primitives.
- Multi-language code generation.
- Direct Anthropic SDK path (for faster `produce` calls); LLM retries; prompt
  cache management; multi-process orchestration.

### Repo deliverable

The Clojure/babashka library plus the Claude Code skill that pairs with it
(per `GOAL.md`).

## 2. Spec data model

Every `feature`/`produce` spec has a `:cases` map. Each case key is a dispatch
tag. The `:input` schema for each case is a Malli tuple whose first element is
the tag; `:output` is the bare returned value schema. Per-case `:desc` and
`:examples` are optional. Single-case and multi-case specs use the same shape.

### `feature` spec

```clojure
{:id   :compute
 :lang :babashka                ; default :babashka; also :clojure
 :desc "Evaluate a Clojure expression string in a babashka sci sandbox."
 :cases
 {:eval
  {:input    [:tuple [:= :eval] :string]
   :output   :double
   :desc     "Read and eval the expression; return the numeric result."
   :examples [{:in [:eval "(+ 1 2)"]    :out 3.0}
              {:in [:eval "(* 2.5 4)"] :out 10.0}]}}}
```

### `produce` spec

Same shape, no `:lang` (every call is an LLM call):

```clojure
{:id   :translate-query
 :desc "Translate natural-language queries into Clojure-computable forms."
 :cases
 {:translate
  {:input    [:tuple [:= :translate] :string]
   :output   :string
   :examples [{:in [:translate "what is two plus two"] :out "(+ 2 2)"}]}}}
```

### Multi-case example (`ui`)

```clojure
{:id   :ui
 :lang :babashka
 :desc (drill/prompt
        "A TUI built with babashka's built-in tools."
        "Manages a single input field, a confirm dialog, a busy"
        "indicator, and a result display.")
 :cases
 {:get+wait-query
  {:input    [:tuple [:= :get+wait-query]]
   :output   :string
   :desc     "Block until the user submits a query; return what they typed."
   :examples [{:in [:get+wait-query] :out "what is 2 + 2"}]}

  :confirm+wait-query
  {:input    [:tuple [:= :confirm+wait-query] :string]
   :output   :boolean
   :desc     "Show the value to the user; return their yes/no."
   :examples [{:in [:confirm+wait-query "(+ 2 2)"] :out true}]}

  :set-result
  {:input    [:tuple [:= :set-result] :string]
   :output   :boolean
   :desc     "Display the computed result; ack with true on success."}

  :in-progress
  {:input    [:tuple [:= :in-progress] :boolean]
   :output   :boolean
   :desc     "Toggle the busy indicator; ack with true."}}}
```

### Top-level spec keys

- `:id` — keyword, unique across the registry.
- `:lang` — `feature` only; `:babashka` (default) or `:clojure`.
- `:desc` — overall description of the unit; per-case `:desc` adds
  operation-specific detail.
- `:cases` — map of `case-tag → {:input, :output, :desc?, :examples?}`.

drill rejects malformed specs with `:drill/invalid-spec` and a humanized Malli
error.

### Call convention

```clojure
(thing :tag & args)
```

drill packs `[:tag & args]` into a tuple, validates against the case's
`:input`, dispatches to the impl with the packed tuple, validates the result
against `:output`, and returns the bare output value.

```clojure
(compute :eval "(+ 1 2)")                  ;; => 3.0
(process :translate "what is two plus two") ;; => "(+ 2 2)"  (LLM call; output not strictly deterministic)
(ui :in-progress true)                     ;; => true
(ui :confirm+wait-query "(+ 2 2)")         ;; => true/false
(ui :get+wait-query)                       ;; => "what is 2 + 2"
```

## 3. File layout

```
deps.edn / bb.edn
src/
  drill/
    core.clj       ; public API: feature, produce, prompt, reg-main, main
    spec.clj       ; Malli schemas for the spec map itself
    registry.clj   ; descriptions / implementations atoms + lookup + register-impl!
    runtime.clj    ; wrap-feature: pack args, validate input/output, dispatch
    generator.clj  ; shells out to `claude`, parses, writes the file
    drift.clj      ; spec hashing + drift detection
    prompt.clj     ; builds the LLM prompt from a spec
    produce.clj    ; runtime LLM path for `produce` specs
easy-calc.clj      ; v1 dummy app (top-level for now)
drill_generated/   ; committed; one .clj file per :feature id
  ui.clj
  compute.clj
skills/
  drill-feature-author/
    SKILL.md
test/
  drill/...
  drill_generated/   ; auto-generated example-based tests, one per feature
```

drill expects `drill_generated/` at the project root and on the classpath via
`bb.edn` / `deps.edn`. v1 uses convention; configurability can come later.

## 4. Two-mode lifecycle

`drill.core/state` carries `{:generate? <bool>}`. Both modes load the user's
app file the same way.

### Run mode

1. User runs `bb easy-calc.clj` (or evaluates the file at the REPL).
2. Each `(def x (drill/feature {...}))` looks up the impl for `:id` in
   `drill.registry/implementations` and returns it. If the impl isn't loaded
   yet, drill calls `(require 'drill-generated.<id>)` — that namespace
   self-registers on load.
3. `(drill/reg-main drill-main)` registers the user's main fn.
4. `(drill/main args)` runs it.
5. `produce` calls go through `drill.produce/call` → `claude -p` →
   schema-validated result.

### Generate mode

1. Same load, but `:generate? true`.
2. Each `(drill/feature {...})` only registers the spec in
   `drill.registry/descriptions` and returns a stub fn so the rest of the file
   loads cleanly.
3. drill iterates `descriptions`:
   - For each `feature`: hash the spec; if `drill_generated/<id>.clj` exists
     with a matching `;; spec-hash: <hash>` header line, skip. Otherwise call
     Claude Code, write the file, embed the hash, run the smoke check.
   - `produce` specs need no codegen.
4. drill prints a summary (`generated`, `regenerated`, `unchanged`, `failed`)
   and exits non-zero if anything failed.

### Generated file shape

```clojure
;; AUTO-GENERATED by drill.
;; spec-hash: 9f3a3b7c8d12e4f5
;; Manual edits will be overwritten on regeneration.
(ns drill-generated.compute
  (:require [sci.core :as sci]
            [drill.registry :as drill-registry]))

(defn -impl [[_tag expr]]
  (double (sci/eval-string expr)))

(drill-registry/register-impl! :compute -impl)
```

- `-impl` takes the packed `[tag & args]` tuple. Multi-case impls `case` on
  the first element. drill's runtime wrapper has already validated the input.
- Self-registration on load avoids a separate manifest file. The file is the
  source of truth for "what's generated"; the header line is the source of
  truth for "which spec version this implements."

### Drift detection

- Spec hash = SHA of canonicalized spec EDN (sorted keys, normalized
  whitespace).
- `(drill/check)` walks all registered specs, compares each against its file's
  header hash, and reports `:fresh`, `:stale`, or `:missing`. Exits non-zero
  in CI mode if anything is not `:fresh`.
- `(drill/regen-stale)` regenerates only stale ones; `(drill/regen :id)`
  forces a single one; `(drill/regen-all)` forces everything.

## 5. REPL & CLI surface

### Authoring API

- `(drill/feature spec)` — register or look up. Returns a callable.
- `(drill/produce spec)` — same shape, runtime LLM.
- `(drill/prompt & lines)` — joins lines with `\n`.
- `(drill/reg-main f)` — register the user's main fn.
- `(drill/main & args)` — entrypoint; dispatches by flags.

### REPL operations

- `(drill/list)` — all specs with id, kind, status (`:fresh`/`:stale`/
  `:missing`).
- `(drill/check)` — like `list` but exits non-zero on stale/missing.
- `(drill/regen :compute)` — regenerate one.
- `(drill/regen-stale)` — regenerate all stale.
- `(drill/regen-all)` — force-regenerate everything.
- `(drill/diff :compute)` — show what would change without writing.

### CLI flags (parsed by `drill/main`)

- (none) → run user's `main-fn`.
- `--generate` / `--regen-stale` → regenerate stale, exit.
- `--regen-all` → force-regenerate, exit.
- `--check` → exit 0 if all fresh, 1 otherwise.
- `--list` → print spec inventory.

## 6. LLM integration

### Code generation (`feature`)

`drill.generator/gen-feature!`:

1. Build a prompt via `drill.prompt/build` — includes the spec EDN, sibling
   feature summaries (id, desc, case schemas — so Claude knows what's
   available to `require`), the target language, the expected file shape, and
   a pointer to the `drill-feature-author` skill.
2. `claude -p "<prompt>"` (headless one-shot). Capture stdout.
3. Parse — expect a single fenced ```clojure ... ``` block. Reject anything
   else and surface the raw response with a `:drill/generation-failed` error.
4. Prepend the auto-generated header and write to
   `drill_generated/<id>.clj`.
5. Smoke check: `require` the file, confirm `:id` got registered, run
   `:examples` through the impl as assertions. On failure: keep the file
   (for inspection) and mark it broken in `(drill/list)`.

### Runtime `produce`

`drill.produce/call` for v1 also shells out to `claude -p` per call — single
integration path, no API key handling. The interface is swappable so a later
direct Anthropic SDK path slots in without changing user code. Tradeoff:
multi-second latency per call. Acceptable for interactive use.

### Shipped skill

Path: `skills/drill-feature-author/SKILL.md`.

Frontmatter:

```
---
name: drill-feature-author
description: Use when generating or modifying an implementation file for a
  drill feature. Activated by drill.generator when shelling out to claude.
---
```

Body covers:

- The spec model — `:id`, `:lang`, `:cases`, the tag-in-input convention.
- The file shape — `ns drill-generated.<id>`, the `-impl` fn,
  `(drill-registry/register-impl! :id -impl)` at the bottom. Claude must not
  write the header — drill prepends it.
- The validation contract — drill validates input/output via Malli before/
  after; `-impl` may assume valid input and must return valid output.
- Dispatch — `-impl` receives the packed `[tag & args]` tuple; multi-case
  impls `case` on the first element.
- Sibling features — call via
  `((drill.registry/lookup :other-id) :tag arg)` or require the sibling's
  generated ns directly.
- One worked example end-to-end (the `compute` spec → its generated file).
- Constraints — pure where possible, no side effects unless the description
  calls for them, prefer babashka-compatible APIs when `:lang :babashka`.

## 7. Testing & error model

### Testing drill itself

- Unit tests for `spec`, `drift`, `prompt`, `registry`.
- `generator` and `produce` tested behind a swappable `llm-call` fn that
  returns canned responses in CI (no `claude` shell-out).
- End-to-end test: `bb easy-calc.clj --generate` with a stub LLM that returns
  a known-good `compute.clj`, then runs the calculator headlessly and asserts
  the result.

### Example-based tests

- For each `feature` with `:examples`, drill emits
  `test/drill_generated/<id>_test.clj` that runs every example through the
  feature and asserts equality with `:out`. Regenerating the feature
  regenerates the test file.
- `produce` examples are used as few-shot prompt context only; not asserted
  (output is non-deterministic).

### Error model

All errors use `ex-info` with a typed `:type` key:

- `:drill/invalid-spec` — spec failed its Malli schema. Payload: humanized
  error.
- `:drill/generation-failed` — `claude` returned something unparseable.
  Payload: raw response, parse error. Bad file (if written) kept for
  inspection.
- `:drill/generated-impl-broken` — smoke check failed. Payload: failing
  example, actual vs expected.
- `:drill/input-invalid` / `:drill/output-invalid` — runtime validation
  failure. Payload: Malli error, tag, offending value.
- `:drill/impl-missing` — lookup miss in run mode. Suggests
  `bb <app> --generate`.

Each error names the offending spec `:id` and case `:tag` so a stack trace
points where debugging starts.

### Out of scope for v1

LLM retries; prompt cache management; multi-process coordination;
observability beyond stdout logs.
