---
name: nilify
description: Entry point for nilify projects. Detects nil/ folder, explains the spec tree model, and routes to nilify subskills. Use when you see a nil/ folder or the user mentions nilify specs.
---

# nilify

You are working in a project that uses **nilify** -- a language-agnostic system specification language. The `nil/` folder defines the system's architecture as a tree of specs. Specs are the primary artifact; implementations are derived.

## When to use this skill

- You see a `nil/` folder in the repository
- The user mentions "nil", "nilify specs", "nilify tree", or "nilify"
- The user asks to define, evolve, or generate from system specs

## The nilify model

nilify defines systems as a hierarchical tree:

```
root
  system (:tech, :provides, :connects-to)
    layer (ordered bottom-to-top)
      feature (:desc, :internals)
```

**root** -- one per project, contains systems.

**system** -- a deployable unit. Has `:id`, `:tech` (free-form string like "react", "http-server babashka"), `:desc`. Exposes interfaces via `:provides`, consumes them via `:connects-to`.

**layer** -- ordered bottom-to-top within a system. Dependencies flow downward only. Supports standard layered and onion architectures.

**feature** -- leaf node. The unit of generation. Has `:id`, `:desc`, `:internals` (domain knowledge for the harness).

### Naming conventions

- `:sys/` -- system ids
- `:feat/` -- feature ids
- `:iface/` -- interface names
- `:<feature>/` -- schema fields owned by a feature (e.g. `:domain-model/id`)

### Interfaces

Systems expose interfaces via `:provides` (typed routes/operations grouped under an `:interface` name). Systems consume interfaces via `:connects-to` (a set of `[system interface]` pairs). The consumer doesn't know the interface shape -- only that it connects. The provider owns the contract.

### Shared schemas

Malli schemas are defined as Clojure vars and referenced across the tree. The schema is the contract; the tree is the structure.

## Subskills

Route to these based on what the user is doing:

| Subskill | When to invoke |
|----------|----------------|
| `nilify:author` | User wants to create or evolve specs |
| `nilify:validate` | User wants to check spec consistency |
| `nilify:generate` | User wants to derive implementations from specs |
| `nilify:extract` | User wants to produce nilify specs from existing code |
| `nilify:onboard` | User is new to the project and needs context |
| `nilify:diff` | (Internal) Other nilify skills need to diff spec vs generated state |

## Example spec

```clojure
(def root (nilc/root
           [[:system
             {:id :sys/backend
              :tech "http-server babashka"
              :provides
              {["HTTP GET" ["/"]]
               {:interface :iface/api
                :input []
                :output [:vector spec-todo]}}}
             [:layer
              [:feature
               {:id :feat/api
                :desc "Provides :iface/api"}]
              [:feature
               {:id :feat/database
                :tech "sqlite"
                :desc "Store :feat/domain-model in sqlite"}]]
             [:layer
              [:feature
               {:id :feat/domain-model
                :internals {:domain-model spec-todo}}]]]]))
```

## Key principles

- **Spec is primary.** Code is derived. Fix the spec before fixing the code.
- **Language-agnostic.** `:tech` declares the stack; the harness generates in that language.
- **Dependencies flow down.** Layers are ordered bottom-to-top. No upward calls.
- **Consumer doesn't validate.** It declares what it connects to; the provider owns the contract.
- **Collaborative evolution.** Both human and LLM propose spec changes. Human approves.
