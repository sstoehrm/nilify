# nil-beta: Living spec language for LLM-fused development

## Summary

nil-beta is a babashka project that installs into a target repository. It deploys a self-contained `nil/` folder (spec DSL + runtime + generated implementations) and a Claude Code skill that teaches any harness the full spec lifecycle. The spec is the primary artifact -- concise, human-readable, collaboratively evolved, and validated at runtime. Code is derived from specs and always replaceable.

## Motivation

drill v1 reimplements agentic code generation (prompt building, LLM shell-out, drift detection) that tools like Claude Code + skills already do better. Its "executable program with CLI flags" model conflates the spec language with generation orchestration.

The reboot inverts the direction: nil-beta doesn't drive generation. It provides the spec language and runtime. The harness (Claude Code, superpowers, ralph loop) drives generation using nil specs as contracts. This lets nil focus on what no existing tool does well: maintaining a living, validated, composable system definition that both humans and LLMs can evolve.

## What nil-beta deploys

When installed into a target repo, nil-beta creates:

1. **`nil/` folder** -- self-contained directory with:
   - Spec files (the DSL -- Clojure data with Malli schemas)
   - Runtime code (validation, dispatch, loading)
   - Generated implementations (output of harness-driven generation)

2. **A Claude Code skill** -- teaches the harness the full nil lifecycle and integrates with transitive skills (superpowers, ralph loop)

## The spec language

### Spec structure

Specs are Clojure data. Each spec declares what a unit does, not how to build it:

```clojure
;; nil/features/compute.clj
{:id :compute
 :desc "Evaluate Clojure expressions in a babashka sci sandbox"
 :cases
 {:eval
  {:input  [:tuple [:= :eval] :string]
   :output :double
   :desc   "Parse and evaluate a Clojure expression string, return numeric result"
   :examples [{:in [:eval "(+ 1 2)"] :out 3.0}
              {:in [:eval "(* 2.5 4)"] :out 10.0}]}}}
```

### Spec fields

| Field    | Required | Description |
|----------|----------|-------------|
| `:id`    | yes      | Unique keyword identifying the feature |
| `:cases` | yes      | Map of dispatch tags to case definitions |
| `:desc`  | no       | Natural-language description of the feature's purpose |
| `:deps`  | no       | Vector of feature `:id`s this feature depends on |

Each case:

| Field       | Required | Description |
|-------------|----------|-------------|
| `:input`    | yes      | Malli schema (first element is the dispatch tag) |
| `:output`   | yes      | Malli schema for the return value |
| `:desc`     | no       | Case-level description |
| `:examples` | no       | `[{:in [...] :out ...}]` -- used for verification and as generation context |

### Composition

Specs can declare dependencies on other specs via `:deps`. The `nil/` folder as a whole represents the system's feature graph:

```clojure
{:id :calculator
 :desc "End-to-end calculator pipeline"
 :deps [:translate :compute]
 :cases
 {:calculate
  {:input  [:tuple [:= :calculate] :string]
   :output :double
   :desc   "Take natural language, translate to expression, evaluate"
   :examples [{:in [:calculate "two plus two"] :out 4.0}]}}}
```

### Spec as primary artifact

The spec is the durable system understanding. Unlike SDD plan documents that grow to thousands of unreadable lines, nil specs stay concise because:

- They declare *what*, not *how*
- Malli schemas are compact
- Examples are concrete and few
- Descriptions are short natural language

Generated code is replaceable -- re-derive it anytime. The spec is what you maintain, review, and evolve.

## The runtime

Lives inside `nil/`. Provides:

### Tag-based dispatch

Call a feature like a function with tag-based dispatch:

```clojure
(compute :eval "(+ 1 2)") ;; => 3.0
```

### Malli validation

Input and output are validated against schemas at call boundaries. Validation errors include the spec id, tag, value, and human-readable explanation.

### Implementation loading

The runtime loads generated implementations from `nil/`. Implementations register themselves against spec ids. The runtime wires them up for dispatch.

### Verification

Instead of hash-based drift detection (v1), nil-beta uses verification: load the implementation, run all examples from the spec, check schema conformance. This answers "does the code satisfy the spec?" rather than "has the spec changed since generation?"

Verification can be triggered by the skill, by the developer, or by CI.

## The skill

The skill is the primary interface between nil specs and the development harness. It teaches Claude Code (and any harness that reads skills) the full lifecycle:

### Capabilities

1. **Read and understand** the current spec tree in `nil/`
2. **Author new specs** through dialogue with the developer
3. **Evolve existing specs** -- propose schema changes, add cases, refine descriptions. Both human and LLM can initiate changes; the human approves.
4. **Generate implementations** that satisfy specs, using the harness's native code-writing ability (not a shell-out to `claude -p`)
5. **Verify** implementations against specs -- load, run examples, check schema conformance
6. **Integrate with transitive skills:**
   - Superpowers (TDD, brainstorming, SDD) -- use nil specs as contracts for structured development
   - Ralph loop -- use nil specs as objectives for autonomous iteration
   - Any future skill that understands structured contracts

### What the skill does NOT do

- Own the generation process (the harness does)
- Build prompts (the harness's native code generation is better)
- Manage CLI flags or application lifecycle
- Replace superpowers or ralph loop -- it gives them structured input

## nil/ folder structure

```
nil/
  core.clj          Runtime: validation, dispatch, loading, verification
  features/         Spec files, one per feature
    compute.clj
    translate.clj
    ui.clj
  generated/        Implementations produced by the harness
    compute.clj
    translate.clj
    ui.clj
```

## What gets cut from v1

| v1 concept | Disposition |
|------------|-------------|
| `*llm-call*` / prompt building | Cut -- harness generates code natively |
| `--generate` / `--check` / `--list` CLI flags | Cut -- nil is a library, not an application |
| `--verbose` / `--debug` / trace | Cut -- observability is the harness's concern |
| Hash-based drift detection | Replaced by verification (run examples, check schemas) |
| `core/main` / `reg-main` | Cut -- no application entry point |
| `produce` (runtime LLM calls) | Deferred -- could return later, but spec language and feature are the focus |

## What carries forward from v1

| v1 concept | Status |
|------------|--------|
| Malli schemas for input/output | Kept -- core of the spec language |
| Tag-based dispatch | Kept -- clean calling convention |
| Examples as verification | Kept -- strengthened (replaces hash-based drift) |
| Spec validation | Kept -- specs are validated on load |
| Registry pattern | Kept -- implementations register against spec ids |

## Installation

nil-beta is a babashka project. To install into a target repo:

```bash
# From target repo root
bb -Sdeps '{:deps {nil-beta {:local/root "/path/to/nil-beta"}}}' -m nil-beta.init
```

This creates the `nil/` folder structure and deploys the Claude Code skill into `.claude/skills/` or the project's skill directory.

## Open questions

- **Host project integration:** How does the host project's code call into nil? Classpath + require, load-file, or something else? To be determined during implementation.
- **Skill distribution:** Should the skill be a Claude Code plugin, a file dropped into the repo, or both?
- **Multi-language support:** GOAL.md mentions translating to multiple languages. Deferred but the spec language should not preclude it (`:lang` field exists but generation is external).
