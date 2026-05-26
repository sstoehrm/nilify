# nil-beta

A language-agnostic API contract validator for LLM-fused development. Define typed interfaces with Malli schemas, declare systems with tech stacks, and validate that component contracts are compatible -- without executing any code.

Specs are the primary artifact. Implementations are derived in whatever language the system declares. The harness (Claude Code, superpowers, ralph loop) drives generation; nil-beta validates the contracts.

## Feature specs

A feature defines a typed interface -- what goes in, what comes out:

```clojure
;; nil/features/translate.clj
{:id :translate
 :desc "Translate natural-language queries into computable forms"
 :cases
 {:translate
  {:input  [:map [:query :string]]
   :output [:map [:expression :string]]
   :desc   "Convert natural language to an expression"
   :examples [{:in {:query "two plus two"} :out {:expression "(+ 2 2)"}}]}}}
```

| Field    | Required | Description |
|----------|----------|-------------|
| `:id`    | yes      | Unique keyword identifying the feature |
| `:cases` | yes      | Map of case names to typed case definitions |
| `:desc`  | no       | Natural-language description |
| `:deps`  | no       | Vector of feature ids this feature depends on |
| `:lang`  | no       | Implementation language hint (free-form keyword) |

Each case has `:input` and `:output` (Malli schemas), optional `:desc` and `:examples`.

## System specs

A system declares components, their tech stacks, and data flow connections:

```clojure
;; nil/systems/calculator.clj
{:id :calculator
 :desc "Natural language calculator"
 :components
 {:translate {:feature :translate :lang :python}
  :compute   {:feature :compute   :lang :babashka}
  :ui        {:feature :ui        :lang :typescript}}
 :connections
 [[[:translate :translate :output] [:compute :eval :input]]]}
```

Connections declare that one component's output feeds another's input. nil-beta validates that the schemas are compatible.

## Validation

Three levels of validation, no code executed:

1. **Spec validity** -- feature and system specs are well-formed
2. **Example conformance** -- each example's input/output conforms to its schema
3. **Connection compatibility** -- connected schemas are type-compatible (via `malli.generator` sampling)

```bash
bb -e '(require (quote nil.core)) (clojure.pprint/pprint (nil.core/validate-all "nil/features" "nil/systems"))'
```

## Runtime API

For Babashka scripts, nil-beta also provides runtime dispatch with Malli validation:

```clojure
(ns my-app
  (:require [nil.core :as nil]))

(def compute
  (nil/feature
   {:id :compute
    :lang :babashka
    :desc "Evaluate expressions"
    :cases
    {:eval {:input  [:tuple [:= :eval] :string]
            :output :double
            :examples [{:in [:eval "(+ 1 2)"] :out 3.0}]}}}))

;; Once an implementation is registered:
(compute :eval "(+ 2 2)") ;; => 4.0
```

- **`feature`** / **`produce`** -- register a spec, return a callable with I/O validation
- **`system`** -- wraps a function as a component boundary
- **`reg-main`** / **`main`** -- CLI entry point utilities

## Folder structure

```
nil/
  core.clj          Public API: validation + runtime
  spec.clj          Feature + system spec schemas (Malli)
  validate.clj      Example conformance + connection compatibility
  features/         Feature spec files (EDN)
  systems/          System spec files (EDN)
```

## Prerequisites

- [Babashka](https://github.com/babashka/babashka)

## Setup

```edn
;; bb.edn
{:paths ["src"]
 :deps  {metosin/malli {:mvn/version "0.16.4"}}}
```

## Running tests

```bash
bb test/runner.clj
```

28 tests, 37 assertions.

## License

All rights reserved.
