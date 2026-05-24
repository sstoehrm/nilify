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

## nil-beta: Current state

A babashka project that installs into target repositories. Deploys two things:

**`nil/` folder** -- self-contained with:
- Runtime: spec validation, registry, tag dispatch with Malli validation, example-based verification
- `features/` -- spec files (EDN data, one per feature)
- `generated/` -- implementations (Clojure source, one per feature)

**Claude Code skill** -- teaches the harness the full nil lifecycle: author specs, evolve them collaboratively, generate implementations, verify against specs. Integrates with superpowers (TDD, brainstorming, SDD) and ralph loop.

### What carries forward
- Malli schemas for input/output contracts
- Tag-based dispatch (`(compute :eval "(+ 1 2)")`)
- Examples as verification (strengthened -- now the primary correctness mechanism)
- Spec validation on load
- Registry pattern for implementations

### What was cut
- `*llm-call*`, prompt building, `--generate` -- harness generates code natively
- CLI application model -- nil is a library
- Hash-based drift detection -- replaced by verification
- `produce` (runtime LLM calls) -- deferred
- Trace/observability -- harness's concern

### Open threads
- **Host integration**: how target projects consume nil (classpath vs load-file)
- **Skill distribution**: plugin vs file-in-repo
- **Multi-language generation**: spec language supports `:lang` but generation is external
- **Richer verification**: static analysis (clj-kondo), generative testing (malli.generator) as post-generation hooks
- **`produce` comeback**: runtime LLM calls may return once the spec language is solid

## Branch history

| Branch | Purpose | Status |
|--------|---------|--------|
| `main` | Initial scaffolding | Base |
| `drill-v1-impl` | Full v1 prototype + trace observability | Complete, preserved for reference |
| `nil-beta` | Reboot as living spec language | Active development |
