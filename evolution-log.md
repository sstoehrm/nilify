# Evolution Log

## Origin: drill-clj (May 2024)

The idea: fuse LLM and classical programming in Clojure/Babashka. Write normal code, declare specs for the parts you want the LLM to fill in, get working implementations back.

Four primitives were envisioned:
- **feature** -- LLM implements a scoped function, committed as source
- **produce** -- LLM runs per-invocation, analyzing input to produce output
- **system** -- LLM implements a whole subsystem (deferred)
- **api** -- LLM implements an API (deferred)

## drill v1: What was built

A working prototype on the `drill-v1-impl` branch:

**Spec language** -- Clojure data maps with Malli schemas defining input/output contracts, dispatch tags, and examples. This turned out to be the strongest part of the design. Compact, readable, and machine-usable.

**Two-mode architecture** -- A single `generate?` boolean flipped the entire system between "generate implementations" and "run implementations." Feature calls returned stubs during generation, real dispatch during execution.

**Code generation pipeline** -- `drill.prompt` built LLM prompts from specs, `drill.generator` shelled out to `claude -p`, extracted fenced code blocks, wrote files with spec-hash headers, and ran smoke checks against examples.

**Drift detection** -- SHA-256 of canonicalized specs compared against headers in generated files. Status: fresh/stale/missing. Enabled `--check` for CI and `--generate` to regenerate stale implementations.

**Runtime** -- Tag-based dispatch with Malli validation at input/output boundaries. Call a feature like a function: `(compute :eval "(+ 1 2)")`.

**Test suite** -- 70 tests covering all modules. LLM calls fully stubbed via dynamic vars.

## drill v1: What we learned

The spec-as-contract pattern worked well. Malli schemas served triple duty: guiding the LLM, validating at runtime, and powering smoke tests.

But the generation pipeline was the wrong abstraction. drill v1 was reimplementing what Claude Code + skills already do -- and doing it worse. The "executable program with CLI flags" model conflated the spec language (valuable) with generation orchestration (redundant).

The drift detection mechanism answered the wrong question. "Has the spec changed?" is less useful than "Does the implementation still satisfy the spec?"

## The reboot: invert the direction

drill v1 tried to be the driver. nilify provides the spec language, and lets the harness drive generation. The spec is the primary artifact, not the code.

## nilify v2: Language-agnostic schema validator

Stripped the Babashka runtime. nilify became a pure API contract validator: feature specs define typed interfaces, system specs declare components with tech stacks and connections, validation checks schema compatibility without executing code.

### Research context

Surveyed the landscape: CUE (lattice-based schema subsumption), Structurizr/C4 (architecture-as-code without typed contracts), Kiro/Tessl/Spec Kit (SDD tools with prose specs), TypeSpec/Smithy (API IDLs without system topology). nilify occupies a unique niche: typed, machine-checkable contracts at the system architecture level, designed for LLM consumption.

## nilify v3: Hierarchical system specification language

The flat feature/system model wasn't expressive enough. The spec language evolved into a tree:

### Node types

- **`root`** -- one per project, contains systems
- **`system`** -- a deployable unit with `:id`, `:tech` (free-form string), `:desc`. Contains layers. Exposes interfaces via `:provides`, consumes them via `:connects-to`.
- **`layer`** -- ordered bottom-to-top within a system. Dependencies flow downward only.
- **`feature`** -- leaf node, the unit of generation. Has `:id`, `:desc`, `:internals`.

### Naming conventions

- `:sys/` -- system ids
- `:feat/` -- feature ids
- `:iface/` -- interface names
- `:<feature>/` -- schema fields owned by a feature (e.g. `:domain-model/id`)

### Interfaces

Systems expose interfaces via `:provides` (typed routes/operations grouped under an `:interface` name). Systems consume via `:connects-to` (a set of `[system interface]` pairs). The consumer doesn't validate the interface shape -- it only declares that it connects. The provider owns the contract.

### Key design decisions

- **`:tech` replaces `:lang`** -- free-form string ("react", "http-server babashka", "sqlite")
- **`:internals`** -- domain knowledge embedded in features for harness context
- **Shared Malli schemas** -- defined as Clojure vars, referenced across the tree
- **Layer ordering** -- bottom-to-top, supports standard layered and onion architectures
- **Intra-layer dependencies** -- features in the same layer can depend on siblings

## nilify skill suite

Seven skills that compose with superpowers and ralph loop:

| Skill | Purpose |
|-------|---------|
| **nilify** | Entry point. Detects nilify projects, explains the model, routes to subskills |
| **nilify:onboard** | Wraps superpowers onboarding with nilify-specific architecture context |
| **nilify:author** | Create and evolve specs through collaborative dialogue |
| **nilify:validate** | Schema validation, example conformance, connection compatibility |
| **nilify:generate** | Orchestrate implementation from specs via superpowers (layer order, per-feature subagents) |
| **nilify:extract** | Reverse-engineer nilify specs from existing codebases |
| **nilify:diff** | Utility: diff spec state vs last generated state |

Skills don't reimplement orchestration -- they provide structured input (the spec tree) and tell the agent which superpowers skill to use for each phase. nilify is the context layer; superpowers is the execution layer.

## Current state

**Runtime library** (`src/nil/`):
- `spec.clj` -- Malli schemas for feature specs (with `:deps`, `:tech`) and system specs (with `:components`, `:connections`, `:provides`)
- `validate.clj` -- example conformance checking, connection compatibility via `malli.generator` sampling
- `core.clj` -- public API: `load-spec`, `load-system`, `validate`, `validate-all`, plus optional runtime dispatch (`feature`, `produce`, `system`)

**Skill suite** (`skills/nilify/`):
- 7 skills covering the full lifecycle from onboarding through generation

**Examples** (`examples/`):
- `easy-calc.clj` -- single-system TUI calculator (babashka)
- `todo.clj` -- multi-system CRUD app (react frontend + babashka backend with sqlite)

**Tests**: 28 tests, 37 assertions, 0 failures.
