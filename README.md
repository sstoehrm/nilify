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
nilify validate   # Validate the tree spec (nil/root.clj)
nilify spec       # Print the complete spec reference (all fields documented)
nilify update     # Update nilify CLI to the latest version
nilify version    # Show version
```

For a complete reference of every field with documentation, run `nilify spec`.

## Quick example

This shows two connected systems with a subsystem; see [`examples/easy-calc.clj`](examples/easy-calc.clj) for a simpler single-system starting point.

```clojure
(ns todo
  (:require [nilify.core :as nilify]))

(def spec-todo
  [:map
   {:registry {:domain-model/id :int}}
   :domain-model/id
   [:name :string]
   [:body :string]
   [:priority [:enum :high :medium :low]]
   [:status [:enum :new :in-progress :done]]])

(nilify/root
 [[:system
   {:id :sys/frontend
    :tech "react"
    :connects-to #{[:sys/backend :iface/backend-api]}}
   [:layer
    [:feature
     {:id :feat/search-ui
      :desc (nilify/prompt
             "Search frame input field")
      :internals {:query-language
                  {"#id" "search for <id>"
                   "<priority" "search for all <priorities"
                   "*status" "search for a *status"
                   :else "Fulltext search on everything"}}}]
    [:feature
     {:id :feat/todo-list-ui
      :desc (nilify/prompt
             "List with all of the todos with :domain-model/id, :name, :priority and :status, filtered by the :feat/search-ui")}]
    [:feature
     {:id :feat/todo-editor
      :desc (nilify/prompt
             "Editing the todo for all fields excl. :domain-model/id.")}]]]
  [:system
   {:id :sys/backend
    :tech "http-server babashka"
    :desc (nilify/prompt
           "HTTP server providing CRUD operations for todos.")
    :provides
    [:iface/backend-api]}
   [:subsystem
    {:id :sub/main
     :provides
     {["HTTP GET" ["/"]]
      {:interface :iface/backend-api
       :input []
       :output [:vector spec-todo]}
      ["HTTP POST" ["/"]]
      {:interface :iface/backend-api
       :input spec-todo
       :output []}
      ["HTTP UPDATE" ["/" :domain-model/id]]
      {:interface :iface/backend-api
       :input spec-todo
       :output []}}}
    [:layer
     [:feature
      {:id :feat/api
       :desc (nilify/prompt "Provides :iface/backend-api")}]
     [:feature
      {:id :feat/database
       :tech "sqlite"
       :desc (nilify/prompt
              "Store the :feat/domain-model inside of sqlite")}]]
    [:layer
     [:feature
      {:id :feat/domain-model
       :internals {"domain-model" spec-todo}}]]]]])
```

## Spec tree

nilify defines systems as a hierarchical tree:

```
root
  system (:tech, :desc, :provides, :connects-to)
    subsystem (:uses, :provides)        ; optional; groups layers
      layer (ordered top-first)
        feature (:desc, :internals)
```

- **root** -- one per project; the file's last form, `(nilify/root [...])`
- **system** -- deployable unit. Advertises interfaces by name in `:provides`; consumes them via `:connects-to`. A system's children are all layers OR all subsystems
- **subsystem** -- groups layers and owns the *typed* interface definitions (`:provides`); declares cross-subsystem use via `:uses`
- **layer** -- ordered top-first: the first layer is topmost/usable, later layers are lower/foundational; lower layers are private to the subsystem
- **feature** -- leaf node; the unit of generation

### Naming conventions

| Prefix | Meaning | Example |
|--------|---------|---------|
| `:sys/` | System id | `:sys/backend` |
| `:sub/` | Subsystem id | `:sub/main` |
| `:feat/` | Feature id | `:feat/domain-model` |
| `:iface/` | Interface name | `:iface/backend-api` |
| `:<feature>/` | Schema field owned by a feature | `:domain-model/id` |

### Interfaces

Systems advertise which interfaces they expose via `:provides` -- a list of `:iface/...` names. The *typed* route/operation definitions live on a `:subsystem`, whose `:provides` map groups routes under an `:interface` key. Consumers declare `:connects-to` with `[system interface]` pairs -- the provider owns the contract; the consumer just declares the connection.

### Shared schemas

Malli schemas defined as Clojure vars and referenced across the tree:

```clojure
(def spec-todo
  [:map
   {:registry {:domain-model/id :int}}
   :domain-model/id
   [:name :string]
   [:priority [:enum :high :medium :low]]
   [:status [:enum :new :in-progress :done]]])
```

## Validation

Three levels, no code executed:

1. **Structure** -- the tree conforms to the node schemas
2. **References** -- `:connects-to` resolves to a provided interface; advertised interfaces are defined by subsystem routes (and vice versa); `:uses` resolves to a sibling subsystem; ids are unique
3. **Schemas** -- each route `:input`/`:output` is a valid, generatable Malli schema (`[]`/`nil` = empty payload)

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
