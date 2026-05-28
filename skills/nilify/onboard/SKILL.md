---
name: nilify-onboard
description: Onboard to a nilify project. Wraps superpowers onboarding with nilify-specific context -- spec tree, systems, tech stacks, naming conventions.
---

# nilify-onboard

Onboard a new contributor (human or agent) to a nilify project. Builds on the standard superpowers onboarding and adds nilify-specific context.

## When to use this skill

- User is new to the project and asks for an overview
- User asks "what does this project do" or "explain the architecture"
- You need to understand the nilify spec tree before working

## Process

1. **Invoke `superpowers:init`** (if available) to generate or read the standard CLAUDE.md onboarding
2. **Read the nilify spec tree** -- find all `.clj` files under `nil/features/` and `nil/systems/` (or the root spec file if it uses inline `nilc/root`)
3. **Summarize the architecture:**
   - List all systems with their `:tech` and `:desc`
   - For each system, list layers (bottom-to-top) and features
   - List cross-system connections (`:provides` / `:connects-to`)
   - List shared schemas
4. **Present to the user** as a concise architecture overview

## Output format

```
## nil Architecture

### Systems
- :sys/frontend (react) -- connects to :iface/backend-api
- :sys/backend (http-server babashka) -- provides :iface/backend-api

### :sys/backend
  Layer 1 (bottom): :feat/domain-model
  Layer 2 (top): :feat/api, :feat/database (sqlite)

### Shared schemas
  spec-todo: [:map :domain-model/id :name :body :priority :status]

### Interfaces
  :iface/backend-api (provided by :sys/backend)
    HTTP GET / → [:vector spec-todo]
    HTTP POST / → spec-todo → []
```

Keep it short. The spec tree IS the documentation -- don't paraphrase what can be read directly.

## Key principle

The nilify spec tree is the authoritative architecture description. Point the user to the spec files rather than writing a separate explanation that will drift.
