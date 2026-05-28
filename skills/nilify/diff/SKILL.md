---
name: nilify-diff
description: Utility skill for diffing spec state vs generated state. Used by nilify-generate and nilify-validate to determine what changed. Not typically invoked directly by users.
---

# nilify-diff

Compare the current nilify spec tree against the last generated state. Determines which features are new, modified, or removed since the last generation pass.

## When to use this skill

- Invoked by `nilify-generate` to determine what needs regeneration
- Invoked by `nilify-author` after spec changes to show impact
- User asks "what changed" or "what needs regeneration"

## How it works

### 1. Read current spec tree

Load all specs from the project (inline root or `nil/features/` + `nil/systems/`).

### 2. Determine last-generated state

Look for a `.nil/state.edn` file (or similar marker) that records:
- Feature ids that were last generated
- A hash of each feature's spec at generation time
- Timestamp of last generation

If no state file exists, everything is new.

### 3. Compute diff

For each feature in the current spec tree:
- **new** -- feature id not in last-generated state
- **modified** -- feature id exists but spec hash differs
- **unchanged** -- feature id exists and spec hash matches

For each feature in the last-generated state but not in the current tree:
- **removed** -- feature was deleted from specs

### 4. Report

Return a structured diff:

```clojure
{:new       [:feat/search-ui :feat/todo-editor]
 :modified  [:feat/api]
 :unchanged [:feat/domain-model :feat/database]
 :removed   [:feat/old-feature]}
```

Or as a human-readable summary:

```
Spec diff:
  + :feat/search-ui (new)
  + :feat/todo-editor (new)
  ~ :feat/api (modified)
  = :feat/domain-model (unchanged)
  = :feat/database (unchanged)
  - :feat/old-feature (removed)
```

## State file

The state file `.nil/state.edn` is written by `nilify-generate` after a successful generation pass. It contains:

```clojure
{:generated-at "2026-05-27T10:00:00Z"
 :features
 {:feat/domain-model {:hash "a1b2c3" :system :sys/backend}
  :feat/api          {:hash "d4e5f6" :system :sys/backend}}}
```

If the project doesn't have a state file yet, `nilify-diff` reports all features as `:new`.
