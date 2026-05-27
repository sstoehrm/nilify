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

**CLI** -- `bb my-app.clj --generate`, `--check`, `--list`, `--regen-all`. Plus REPL operations for interactive use.

**Observability** -- `drill.trace` with leveled `*level*` var (`:off`/`:info`/`:debug`), `--verbose` and `--debug` CLI flags. Emitted structured events at each pipeline stage.

**Test suite** -- 70 tests covering all modules. LLM calls fully stubbed via dynamic vars.

**Example app** -- `easy-calc.clj` with three features (UI, translate, compute) demonstrating the full workflow.

## drill v1: What we learned

The spec-as-contract pattern worked well. Malli schemas served triple duty: guiding the LLM, validating at runtime, and powering smoke tests. Tag-based dispatch was clean and Clojure-idiomatic.

But the generation pipeline was the wrong abstraction. drill v1 was essentially reimplementing what Claude Code + skills already do -- and doing it worse. Shelling out to `claude -p` with hand-built prompts couldn't match an agentic harness that understands the full project context, can iterate on failures, and integrates with development workflows.

The "executable program with CLI flags" model conflated two concerns: the spec language (valuable) and generation orchestration (redundant). Adding observability to the generation path (the trace feature) highlighted this -- we were building infrastructure for a code-generation workflow that existing tools handle better.

The drift detection mechanism (hash-based) answered the wrong question. "Has the spec changed?" is less useful than "Does the implementation still satisfy the spec?" The former triggers regeneration; the latter enables verification.

## The reboot insight

The key realization: **invert the direction**.

drill v1 tried to be the driver -- it owned the spec, built the prompt, called the LLM, wrote the file. nil-beta provides the spec language and runtime, and lets the harness (Claude Code, superpowers, ralph loop) drive generation. This means:

- No prompt building, no LLM shell-out, no generation pipeline
- The skill teaches the harness how to work with nil specs
- Superpowers and ralph loop can use specs as structured contracts
- drill's value is the spec language itself, not the generation machinery

A second insight: **the spec is the primary artifact, not the code**. SDD-style plan documents grow to thousands of unreadable lines. nil specs stay concise because they declare *what*, not *how*. The generated code is replaceable -- re-derive it anytime. The spec is what you maintain, review, and evolve collaboratively.

## nil-beta v1: Runtime contract layer

Built the core runtime: spec validation, registry, tag dispatch with Malli validation, example-based verification. Feature and produce return callables. Deployed via init script into target repos with a Claude Code skill.

Realized this still only worked for Babashka -- the runtime dispatch tied nil to one language.

## nil-beta v2: Language-agnostic schema validator

Stripped the runtime. nil-beta became a pure API contract validator:

- **Feature specs** define typed interfaces with Malli schemas (input/output per case)
- **System specs** declare components with tech stacks and data flow connections
- **Validation** checks spec validity, example conformance, and connection compatibility (via `malli.generator` sampling)
- No code execution -- validation is schema-level only

Added runtime API back as an optional layer for Babashka scripts (`feature`, `produce`, `system` return callables when you need them).

### Research context

Surveyed the landscape: CUE (lattice-based schema subsumption), Structurizr/C4 (architecture-as-code without typed contracts), Kiro/Tessl/Spec Kit (SDD tools with prose specs), TypeSpec/Smithy (API IDLs without system topology). nil-beta occupies a unique niche: typed, machine-checkable contracts at the system architecture level, designed for LLM consumption.

## nil-beta v3 (in progress): Hierarchical system specification language

The flat feature/system model wasn't expressive enough. Real systems have structure -- layers, subsystems, cross-system interfaces. The spec language evolved into a tree:

### Node types

- **`root`** -- one per project, contains systems
- **`system`** -- a deployable unit with `:id`, `:tech` (free-form string), `:desc`. Contains layers.
- **`layer`** -- ordered bottom-to-top within a system. Dependencies flow downward only (a layer can use anything in layers below it, never above). Supports standard layered architecture and onion architecture.
- **`feature`** -- leaf node, the unit of generation. Has `:id`, `:desc`, `:internals`. Can depend on siblings in the same layer and anything in lower layers.

### Naming conventions

Namespaced keywords distinguish reference types by convention:
- `:sys/` -- system ids (`:sys/frontend`, `:sys/backend`)
- `:feat/` -- feature ids (`:feat/search-ui`, `:feat/domain-model`)
- `:iface/` -- interface names (`:iface/backend-api`)
- `:<feature>/` -- schema fields owned by a feature (`:domain-model/id`)

Fully qualified paths like `:backend/feat/domain-model` are possible but not required.

### Interfaces and connections

Systems expose interfaces via `:provides` -- a map of routes/operations to typed schemas grouped under an `:interface` name. Systems consume interfaces via `:connects-to` -- a simple set of `[system interface]` pairs.

Key design decision: **the consumer doesn't validate the interface shape**. It only declares that it connects to an interface. The provider owns the full contract definition. Validation checks that every referenced interface actually exists. The harness uses the provider's definition to generate both sides.

### Key design decisions

- **`:tech` replaces `:lang`** -- free-form string, more flexible ("react", "http-server babashka", "sqlite")
- **`:internals`** -- domain knowledge embedded in features (query language DSLs, domain models, screen descriptions). Rich context for the harness without being implementation code.
- **Shared Malli schemas** -- defined as Clojure vars and referenced across the tree. The schema is the contract; the tree is the structure.

### Open threads

- What the harness does with the tree (generation order, per-system tech adaptation)
- Whether layers should be explicitly named
- How `:internals` shapes differ across feature types
- Visualization from the spec tree (inspired by Structurizr)

## Branch history

| Branch | Purpose | Status |
|--------|---------|--------|
| `main` | Active development | Current |
| `drill-v1-impl` | Full v1 prototype + trace observability | Preserved for reference |
| `nil-beta` | v1 reboot + v2 schema validator | Merged to main |
