---
name: nilify-validate
description: Validate nilify spec consistency -- spec well-formedness, example conformance against schemas, and connection compatibility between systems. Use when checking specs after authoring or before generation.
---

# nilify-validate

Run schema-level validation on nilify specs. No code is executed.

## When to use this skill

- After authoring or modifying specs (invoked by `nilify-author`)
- Before generating implementations (invoked by `nilify-generate`)
- User asks to "validate", "check", or "verify" the specs
- CI/pre-commit check

## Running validation

One command runs all three levels:

```bash
nilify validate
```

Run it from the project root (the directory containing `nil/`). It loads `nil/root.clj`,
reports per-level pass/fail counts, and exits non-zero if any check fails (suitable for
CI / pre-commit). The spec is evaluated by babashka's bundled sci -- no sandboxing, no
project code executed.

## What the three levels check

### 1. Structure

The whole tree conforms to the node schemas (system / subsystem / layer / feature shapes, required `:id`s, `:provides` / `:connects-to` shapes).

Catches: malformed nodes, missing `:id`, a system body mixing layers and subsystems, bad `:provides` structure.

### 2. References

Cross-reference and visibility integrity:
- every `:connects-to [sys iface]` resolves to a system that advertises `iface` in its `:provides`;
- every interface a system advertises is defined by a route in one of its subsystems (and no route advertises an interface the system doesn't list);
- every `:uses` resolves to a sibling subsystem;
- all ids are unique.

### 3. Schemas

Each subsystem route's `:input`/`:output` is a valid, generatable Malli schema (`[]` or `nil` means an empty payload and is skipped).

## Reporting

Present results concisely:

```
Validation results (nil/root.clj):
  Structure: ok
  References: 1 problem(s)
  Schemas: 0 problem(s)
  - :sys/frontend connects-to :sys/backend :iface/search-api, but that system does not provide it
```

## When validation fails

- **Structure failure** -- fix the malformed node. Invoke `nilify-author` if the user needs help reshaping the tree.
- **References failure** -- a `:connects-to` pair, `:uses` reference, or advertised interface name is unresolvable. Check ids and `:provides` lists for typos or missing entries.
- **Schemas failure** -- a subsystem route's `:input` or `:output` is not a valid Malli schema. Fix the schema definition.
