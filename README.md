# nilify

A language-agnostic system specification language for LLM-fused development. Define your architecture as a tree of typed specs, validate contracts between components, and let the harness generate implementations in any tech stack.

## The idea

Specs are the primary artifact. You declare *what* the system does -- typed interfaces, layer dependencies, cross-system connections -- and the harness (Claude Code, superpowers, ralph loop) derives the code. The spec stays concise and readable; the code is replaceable.

## Quick example

```clojure
(ns easy-calc
  (:require [nil.core :as nilc]))

(def spec-query  [:map [:query :string]])
(def spec-expr   [:map [:expr :string]])
(def spec-result [:map [:result :double]])

(def root (nilc/root
           [[:system
             {:id :sys/easy-calc
              :tech "tui babashka"
              :desc (nilc/prompt
                     "A TUI calculator that takes natural language math"
                     "queries, translates them to Clojure expressions,"
                     "and evaluates them.")}
             [:layer
              [:feature
               {:id :feat/ui
                :desc (nilc/prompt
                       "A TUI with input, confirm, result, and busy screens.")}]]
             [:layer
              [:feature
               {:id :feat/translate
                :desc (nilc/prompt
                       "Translate natural-language queries into"
                       "Clojure-computable expressions.")}]
              [:feature
               {:id :feat/compute
                :desc (nilc/prompt
                       "Evaluate a Clojure expression string"
                       "in a babashka sci sandbox.")}]]]]))
```

## Spec tree

nilify defines systems as a hierarchical tree:

```
root
  system (:tech, :provides, :connects-to)
    layer (ordered bottom-to-top)
      feature (:desc, :internals)
```

- **root** -- one per project, contains systems
- **system** -- a deployable unit with a tech stack. Exposes interfaces via `:provides`, consumes them via `:connects-to`
- **layer** -- ordered bottom-to-top. Dependencies flow downward only
- **feature** -- leaf node. The unit of generation

### Naming conventions

| Prefix | Meaning | Example |
|--------|---------|---------|
| `:sys/` | System id | `:sys/backend` |
| `:feat/` | Feature id | `:feat/domain-model` |
| `:iface/` | Interface name | `:iface/backend-api` |
| `:<feature>/` | Schema field owned by a feature | `:domain-model/id` |

### Interfaces

Systems expose interfaces via `:provides` -- typed routes/operations grouped under an `:interface` name. Consumers declare `:connects-to` with `[system interface]` pairs. The provider owns the contract; the consumer just declares it connects.

### Shared schemas

Malli schemas defined as Clojure vars and referenced across the tree:

```clojure
(def spec-todo
  [:map
   :domain-model/id
   [:name :string]
   [:priority [:enum :high :medium :low]]
   [:status [:enum :new :in-progress :done]]])
```

## Validation

Three levels, no code executed:

1. **Spec validity** -- specs are well-formed per Malli schemas
2. **Example conformance** -- each example's input/output conforms to its schema
3. **Connection compatibility** -- connected schemas are type-compatible (via `malli.generator` sampling)

```bash
bb -e '(require (quote nil.core)) (clojure.pprint/pprint (nil.core/validate-all "nil/features" "nil/systems"))'
```

## Skills

nilify ships a skill suite that composes with [superpowers](https://github.com/anthropics/claude-code-plugins) and ralph loop:

| Skill | Purpose |
|-------|---------|
| `nilify` | Entry point -- detect project, explain model, route to subskills |
| `nilify:onboard` | Onboard to a nilify project with architecture context |
| `nilify:author` | Create and evolve specs through dialogue |
| `nilify:validate` | Run schema validation and compatibility checks |
| `nilify:generate` | Derive implementations from specs via superpowers |
| `nilify:extract` | Reverse-engineer specs from existing codebases |
| `nilify:diff` | Diff spec state vs last generated state |

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

## Examples

- [`examples/easy-calc.clj`](examples/easy-calc.clj) -- single-system TUI calculator
- [`examples/todo.clj`](examples/todo.clj) -- multi-system CRUD app (React + Babashka + SQLite)

## License

Apache License 2.0. See [LICENSE](LICENSE).
