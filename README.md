# drill

A Clojure/Babashka library that fuses LLM-generated code with classical programming. You write declarative specs; drill generates (and regenerates) implementations via Claude, commits them as reviewable source files, and validates them at both generation time and runtime.

## Two primitives

**`feature`** -- Static code generation. You declare a spec with input/output schemas and examples. Drill calls Claude once to produce an implementation file, runs your examples as smoke tests, and writes the result to `drill_generated/`. From then on it's just regular Clojure code you can read, review, and commit.

**`produce`** -- Runtime LLM. Same spec shape, but every call dispatches to Claude live. Use this for tasks that need fresh reasoning per invocation (e.g. natural-language translation).

## Quick example

```clojure
(ns easy-calc
  (:require [drill.core :as drill]))

(def compute
  (drill/feature
   {:id   :compute
    :lang :babashka
    :desc "Evaluate a Clojure expression string in a babashka sci sandbox."
    :cases
    {:eval
     {:input    [:tuple [:= :eval] :string]
      :output   :double
      :examples [{:in [:eval "(+ 1 2)"] :out 3.0}
                 {:in [:eval "(* 2.5 4)"] :out 10.0}]}}}))

(compute :eval "(+ 2 2)") ;; => 4.0
```

Generate the implementation, then run:

```bash
bb easy-calc.clj --generate   # calls Claude, writes drill_generated/compute.clj
bb easy-calc.clj               # runs the app
```

## How specs work

Every spec has:

| Field    | Required | Description                                        |
|----------|----------|----------------------------------------------------|
| `:id`    | yes      | Unique keyword identifying the feature              |
| `:cases` | yes      | Map of dispatch tags to case definitions             |
| `:lang`  | no       | `:babashka` (default) or `:clojure`                  |
| `:desc`  | no       | Natural-language description passed to the LLM       |

Each case defines:

| Field       | Required | Description                              |
|-------------|----------|------------------------------------------|
| `:input`    | yes      | Malli schema (first element is the tag)   |
| `:output`   | yes      | Malli schema for the return value         |
| `:desc`     | no       | Case-level description for the LLM        |
| `:examples` | no       | `[{:in [...] :out ...}]` -- used for smoke tests and as LLM context |

Calling a feature or produce function uses tag-based dispatch:

```clojure
(compute :eval "(+ 1 2)")  ;; dispatches to the :eval case
```

Inputs and outputs are validated against the Malli schemas at runtime.

## Drift detection

Drill tracks whether your spec has changed since the implementation was generated:

- **`:fresh`** -- spec hash matches the generated file header
- **`:stale`** -- spec changed; regeneration needed
- **`:missing`** -- no generated file exists yet

```bash
bb my-app.clj --check    # prints status for each feature, exits 1 if any are stale/missing
```

## CLI flags

Add this to the bottom of your script to enable CLI mode:

```clojure
(drill/reg-main my-main-fn)

(when (= *file* (System/getProperty "babashka.file"))
  (apply drill/main *command-line-args*))
```

Then:

```bash
bb my-app.clj --generate      # generate missing/stale implementations
bb my-app.clj --check         # verify all features are fresh
bb my-app.clj --list          # show spec inventory with status
bb my-app.clj --regen-all     # force-regenerate everything
bb my-app.clj                 # run your main function
```

## REPL usage

```clojure
(drill/list)             ;; all specs with status
(drill/check)            ;; print status, exit 1 if stale
(drill/regen :compute)   ;; regenerate one feature
(drill/regen-stale)      ;; regenerate all stale/missing
(drill/regen-all)        ;; force-regenerate everything
(drill/diff :compute)    ;; show hash comparison without regenerating
```

## Prerequisites

- [Babashka](https://github.com/babashka/babashka)
- [Claude CLI](https://docs.anthropic.com/en/docs/claude-code) (`claude` must be on your PATH for generation)

## Setup

Clone the repo and add it to your Babashka classpath, or depend on the source directly. The only external dependency is [Malli](https://github.com/metosin/malli) for schema validation.

```edn
;; bb.edn
{:paths ["src"]
 :deps  {metosin/malli {:mvn/version "0.16.4"}}}
```

## Running tests

```bash
bb test/runner.clj
```

59 tests covering all modules. Tests never call Claude -- the LLM is stubbed via `drill.generator/*llm-call*`.

## Architecture

```
src/drill/
  spec.clj       Malli schemas for validating spec structure
  registry.clj   Atom-based registration of specs and implementations
  drift.clj      SHA-256 hashing for change detection
  prompt.clj     Builds LLM prompts from specs
  generator.clj  Calls Claude, parses output, writes files, runs smoke checks
  runtime.clj    Tag-based dispatch with input/output validation
  produce.clj    Runtime LLM path (one call per invocation)
  core.clj       Public API
```

## License

All rights reserved.
