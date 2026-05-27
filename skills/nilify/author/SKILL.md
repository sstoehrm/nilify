---
name: nilify:author
description: Create and evolve nilify specs through dialogue. Produces spec files with the tree structure (root, system, layer, feature). Use when the user wants to define or modify the system architecture.
---

# nilify:author

Create new nilify specs or evolve existing ones through collaborative dialogue.

## When to use this skill

- User wants to create a new nilify project from scratch
- User wants to add a system, layer, or feature to an existing spec
- User wants to refine schemas, add examples, change interfaces
- User says "add a feature", "new system", "update the spec"

## Process

### New project

1. **Discuss the system** -- what does it do? What are the major components?
2. **Identify systems** -- what deploys independently? What tech stack for each?
3. **Identify features per system** -- what are the units of functionality?
4. **Layer the features** -- which are foundational? Which depend on others? Bottom-to-top.
5. **Define interfaces** -- if multiple systems, what connects them? `:provides` on the provider, `:connects-to` on the consumer.
6. **Define shared schemas** -- what data flows between features? Define as Malli schemas.
7. **Write the spec file** -- one file per project using `nilc/root`, or separate files in `nil/features/` and `nil/systems/`
8. **Invoke `nilify:validate`** to check consistency

### Evolving existing specs

1. **Read the current spec tree** (invoke `nilify:diff` to understand current state)
2. **Discuss the change** with the user -- what and why
3. **Make the change** -- add/modify/remove nodes
4. **Invoke `nilify:validate`** to check nothing broke
5. **Explain the impact** -- which features/systems are affected

## Authoring guidelines

**Features should be small.** If a feature's `:desc` + `:internals` takes more than 30 seconds to read, split it.

**Layer from the bottom up.** Start with the domain model / core logic, add infrastructure above, add UI/API on top.

**Use `:internals` for domain knowledge.** Query language DSLs, domain models, screen descriptions, workflow rules -- anything that helps the harness generate but isn't a typed contract.

**Use shared Malli schemas for contracts.** Define them as Clojure vars at the top of the file. Reference them in `:provides`, `:internals`, and descriptions.

**Follow naming conventions:**
- `:sys/` for systems
- `:feat/` for features
- `:iface/` for interfaces
- `:<feature>/` for schema fields owned by a feature

**`:desc` uses `nilc/prompt`** for multi-line descriptions:
```clojure
:desc (nilc/prompt
       "First line of description"
       "continues here")
```

## Both human and LLM propose changes

You can suggest spec changes -- new features, schema refinements, layer reorganization. Always explain WHY. The user approves all changes.

If the user proposes a change that would break an interface or violate layer dependencies, flag it before making the change.
