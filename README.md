# nilify

A language-agnostic system specification language for LLM-fused development. Define your architecture as a tree of typed specs, validate contracts between components, and let the harness generate implementations in any tech stack.

## Why nilify

Spec-driven development tools (Spec Kit, Tessl, Kiro, superpowers SDD) produce prose specifications. As projects grow, these specs become thousands of lines of unreviewable markdown -- you can't see the architecture by skimming. Specs and code drift apart silently.

nilify uses structure instead of prose. A tree of typed specs is scannable: you see all systems, their layers, their features in a few screens. The tree conveys architecture in a way text can't, and machine-checkability (Malli schemas + connection compatibility) keeps specs consistent with code over time.

## The idea

Specs are the primary artifact. You declare *what* the system does -- typed interfaces, layer dependencies, cross-system connections -- and the harness (Claude Code, superpowers, ralph loop) derives the code. The spec stays concise and readable; the code is replaceable.

## Install

Requires [Babashka](https://github.com/babashka/babashka).

```bash
curl -fsSL https://raw.githubusercontent.com/sstoehrm/nilify/main/install.sh | bash
```

Or download the `nilify` script directly into your PATH:

```bash
curl -fsSL https://raw.githubusercontent.com/sstoehrm/nilify/main/nilify -o ~/.local/bin/nilify && chmod +x ~/.local/bin/nilify
```

## Usage

```bash
nilify init       # Create nil/ folders, deploy skills into .claude/skills/
nilify validate   # Validate specs in nil/features/ and nil/systems/
nilify spec       # Print the complete spec reference (all fields documented)
nilify update     # Update nilify CLI to the latest version
nilify version    # Show version
```

For a complete reference of every field with documentation, run `nilify spec`.

## Quick example

```clojure
(ns easy-calc
  (:require [nilify.core :as nilify]))

(def spec-query  [:map [:query :string]])
(def spec-expr   [:map [:expr :string]])
(def spec-result [:map [:result :double]])

(def root (nilify/root
           [[:system
             {:id :sys/easy-calc
              :tech "tui babashka"
              :desc (nilify/prompt
                     "A TUI calculator that takes natural language math"
                     "queries, translates them to Clojure expressions,"
                     "and evaluates them.")}
             [:layer
              [:feature
               {:id :feat/ui
                :desc (nilify/prompt
                       "A TUI with input, confirm, result, and busy screens.")}]]
             [:layer
              [:feature
               {:id :feat/translate
                :desc (nilify/prompt
                       "Translate natural-language queries into"
                       "Clojure-computable expressions.")}]
              [:feature
               {:id :feat/compute
                :desc (nilify/prompt
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
nilify validate
```

## Skills

nilify ships a skill suite that composes with [superpowers](https://github.com/anthropics/claude-code-plugins) and ralph loop:

| Skill | Purpose |
|-------|---------|
| `nilify` | Entry point -- detect project, explain model, route to subskills |
| `nilify-onboard` | Onboard to a nilify project with architecture context |
| `nilify-author` | Create and evolve specs through dialogue |
| `nilify-validate` | Run schema validation and compatibility checks |
| `nilify-generate` | Derive implementations from specs via superpowers |
| `nilify-extract` | Reverse-engineer specs from existing codebases |
| `nilify-diff` | Diff spec state vs last generated state |

## Examples

- [`examples/easy-calc.clj`](examples/easy-calc.clj) -- single-system TUI calculator
- [`examples/todo.clj`](examples/todo.clj) -- multi-system CRUD app (React + Babashka + SQLite)

## Development

```bash
bb test/runner.clj    # Run tests
```

## License

Apache License 2.0. See [LICENSE](LICENSE).
