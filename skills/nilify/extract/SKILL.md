---
name: nilify-extract
description: Analyze an existing codebase and produce a nilify spec tree from it. Reverse of nilify-generate -- goes from code to specs. Use when adopting nil in an existing project.
---

# nilify-extract

Read an existing codebase and produce a nilify spec tree that describes its architecture.

## When to use this skill

- User wants to adopt nilify in an existing project
- User says "extract specs", "nilify this project", "create specs from code"
- User wants to understand an existing codebase through the nilify lens

## Process

### 1. Survey the project

- Read project config (package.json, bb.edn, Cargo.toml, etc.) to identify tech stack
- Scan directory structure for system boundaries (frontend/, backend/, services/)
- Read README, CLAUDE.md, or similar docs for architecture context

### 2. Identify systems

Each deployable unit becomes a system. Look for:
- Separate directories with their own config (package.json, bb.edn)
- Docker services
- Distinct tech stacks in the same repo (monorepo)
- Single-system projects are common -- that's fine

For each system, determine `:tech` from the project config and dependencies.

### 3. Identify features

Within each system, look for natural units of functionality:
- Modules, namespaces, or packages with clear boundaries
- API route handlers / controllers
- Database access layers
- Domain models
- UI components or pages

Each becomes a feature with `:desc` derived from the code and `:internals` capturing key domain knowledge (schemas, routes, rules).

### 4. Layer the features

Analyze import/dependency graphs to determine layering:
- What depends on nothing else? → bottom layer
- What depends on bottom-layer features? → next layer up
- What depends on everything? → top layer

### 5. Identify interfaces

Look for cross-system communication:
- HTTP clients/servers with matching routes
- Message queues, event buses
- Shared databases
- RPC/gRPC definitions

These become `:provides` on the server side and `:connects-to` on the client side.

### 6. Extract schemas

Look for existing type definitions, validation schemas, or data structures:
- TypeScript interfaces/types
- Malli/Spec schemas in Clojure
- Pydantic models in Python
- Database table definitions

Convert to Malli schemas as shared vars.

### 7. Write the spec

Produce the spec file(s) and present to the user for review. The extracted spec is a starting point -- the user refines it.

### 8. Validate

Invoke `nilify-validate` on the extracted specs.

## Guidelines

**Don't over-extract.** A 100-feature spec tree is useless. Aim for 3-10 features per system. Group related functionality.

**Ask the user.** When boundaries are ambiguous ("is this one feature or two?"), ask rather than guess.

**The extraction is a draft.** Present it and iterate with the user via `nilify-author`.

**Preserve existing naming.** Use the project's own terminology for feature names and descriptions. Don't rename things.
