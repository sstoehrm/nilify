---
name: nilify-generate
description: Derive implementations from nilify specs. Reads the spec tree, respects layer ordering and :tech declarations, dispatches generation via superpowers skills. Use when the user wants to generate code from specs.
---

# nilify-generate

Generate implementations from nilify specs. This skill orchestrates generation -- it reads the tree, determines order and tech stack, and delegates actual code writing to superpowers skills.

## When to use this skill

- User asks to "generate", "implement", or "build" from specs
- User asks to implement a specific feature or system
- After `nilify-author` completes a new spec

## Process

### 1. Read the spec tree

Load all specs. Invoke `nilify-validate` first -- do not generate from invalid specs.

### 2. Invoke `nilify-diff` 

Determine what changed since last generation. Only generate for new or modified features unless the user asks for a full regeneration.

### 3. Determine generation order

Respect layer dependencies -- generate the foundational (lower) layers first within each system:

```
System :sys/backend
  Layer 1 (bottom): :feat/domain-model  ← generate first
  Layer 2 (top):    :feat/api, :feat/database  ← generate second
```

Features within the same layer can be generated in parallel.

### 4. Generate per feature

For each feature, the harness needs:

- **`:tech`** from the containing system -- determines the implementation language
- **`:desc`** -- what the feature does
- **`:internals`** -- domain knowledge, examples, rules
- **Lower layer context** -- what's already implemented below this feature
- **Interface contracts** -- if this feature implements a `:provides` interface, the typed routes/schemas

Use `superpowers:subagent-driven-development` to dispatch one subagent per feature. Each subagent receives:

- The feature spec
- The system's `:tech`
- The `:provides` routes (if applicable)
- Descriptions of features in lower layers

### 5. Cross-system generation

When multiple systems exist, generate each system independently. Cross-system interfaces are contracts -- the provider is generated first, the consumer second.

Order: generate systems that only provide (no `:connects-to`) before systems that consume.

### 6. Verify after generation

After all features are generated, invoke `nilify-validate` again to confirm the implementation matches the spec contracts.

## What this skill does NOT do

- Write code itself -- it dispatches to subagents via superpowers
- Decide implementation details -- that's the subagent's job, guided by `:tech` and `:desc`
- Own the generated files -- code goes wherever the project's conventions dictate

## Integration with superpowers

- **`superpowers:subagent-driven-development`** -- one subagent per feature, with spec review and code quality review
- **`superpowers:dispatching-parallel-agents`** -- features in the same layer generated concurrently
- **`superpowers:test-driven-development`** -- subagents use TDD, with spec examples as the initial test cases
- **`superpowers:verification-before-completion`** -- run `nilify-validate` before claiming done
