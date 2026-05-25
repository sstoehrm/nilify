---
name: nil-beta
description: Full lifecycle management of nil specs -- author, evolve, generate implementations, and verify. Use when working in a repo with a nil/ folder.
---

# nil-beta: Living Spec Lifecycle

You are working in a repository that uses **nil-beta** -- a living spec language for LLM-fused development. The `nil/` folder is the system definition. Specs are the primary artifact; generated code is derived and replaceable.

## When to use this skill

- The user asks to create, modify, or review a nil spec
- The user asks to implement or generate code for a nil feature
- The user asks to verify implementations against specs
- You see a `nil/` folder in the repository
- The user references "nil", "nil-beta", or "specs" in the context of feature development

## Understanding nil/

```
nil/
  core.clj          Runtime: public API (load-spec, feature, verify)
  spec.clj          Spec schema validation (Malli)
  registry.clj      Registration of specs and implementations
  runtime.clj       Tag dispatch with I/O validation
  verify.clj        Example-based verification
  features/         Spec files (one per feature, EDN data)
    compute.clj
    translate.clj
  generated/        Implementations (one per feature, Clojure source)
    compute.clj
    translate.clj
```

**Spec files** in `features/` are EDN maps -- NOT Clojure namespaces. They look like:

```clojure
{:id :compute
 :desc "Evaluate Clojure expressions in a babashka sci sandbox"
 :cases
 {:eval
  {:input  [:tuple [:= :eval] :string]
   :output :double
   :desc   "Parse and evaluate a Clojure expression string"
   :examples [{:in [:eval "(+ 1 2)"] :out 3.0}
              {:in [:eval "(* 2.5 4)"] :out 10.0}]}}}
```

**Generated files** in `generated/` are Clojure source with a namespace, an `-impl` function, and a registration call:

```clojure
(ns nil-generated.compute
  (:require [nil.registry :as reg]))

(defn -impl [[tag & args]]
  (case tag
    :eval (Double/parseDouble (str (load-string (first args))))))

(reg/register-impl! :compute -impl)
```

## Spec authoring

When authoring a new spec:

1. **Discuss intent** with the user -- what does this feature do?
2. **Draft the spec** with `:id`, `:desc`, `:cases`
3. **Define schemas** using Malli. Common patterns:
   - `[:tuple [:= :tag] <arg-types>...]` for input (first element is the dispatch tag)
   - `:string`, `:int`, `:double`, `:boolean`, `:any` for simple outputs
   - `[:map [:key :type] ...]` for structured outputs
4. **Add examples** -- concrete input/output pairs. These serve as verification AND as context for implementation.
5. **Add `:deps`** if this feature depends on other features
6. **Write the spec file** to `nil/features/<id>.clj`

Keep specs concise. The spec IS the system documentation -- if it's too long to read in 30 seconds, it's too complex for a single feature. Split it.

## Spec evolution

Both you and the user can propose spec changes:

- **Adding a case**: new dispatch tag with its own schema and examples
- **Refining a schema**: making types more precise (`:any` -> `:string`)
- **Adding examples**: more verification coverage
- **Adding/removing deps**: changing the feature graph
- **Changing descriptions**: improving clarity for future generation

Always explain WHY you're proposing a change. The user approves all spec changes.

## Generating implementations

When generating an implementation for a spec:

1. **Read the spec** from `nil/features/<id>.clj`
2. **Read `:deps` specs** to understand the feature graph
3. **Write the implementation** to `nil/generated/<id>.clj` following this structure:
   - Namespace: `nil-generated.<id>`
   - Require: `[nil.registry :as reg]` (and any dep namespaces)
   - Define: `(defn -impl [[tag & args]] ...)` with a `case` on tag
   - Register: `(reg/register-impl! :<id> -impl)`
4. **Verify** by running: `bb -e '(do (require (quote nil.core)) (nil.core/load-specs "nil/features") (load-file "nil/generated/<id>.clj") (prn (nil.core/verify :<id>)))'`

The implementation must satisfy:
- All `:examples` produce the expected output
- All outputs conform to the `:output` Malli schema
- All inputs are accepted per the `:input` Malli schema

## Verification

Run verification to check implementations against specs:

```bash
# Verify one feature
bb -e '(do (require (quote nil.core)) (nil.core/load-specs "nil/features") (load-file "nil/generated/compute.clj") (prn (nil.core/verify :compute)))'

# Verify all
bb -e '(do (require (quote nil.core)) (nil.core/load-specs "nil/features") (doseq [f (.listFiles (clojure.java.io/file "nil/generated"))] (load-file (.getPath f))) (run! prn (nil.core/verify-all)))'
```

Verification results:
- `:pass` -- all examples match, all schemas conform
- `:fail` -- at least one example mismatch or schema violation
- `:missing` -- no implementation registered for this spec

## Integration with other skills

### With superpowers:brainstorming
Use nil specs as the output of brainstorming. Instead of a design doc, produce a spec file.

### With superpowers:test-driven-development
The spec's examples ARE the test cases. Write the spec first (with examples), then generate the implementation. Verification replaces the test suite for generated code.

### With superpowers:subagent-driven-development
Each nil spec is a natural task boundary. Dispatch one subagent per spec to generate implementations.

### With ralph-loop
Use nil specs as objectives. Ralph can iterate: generate implementation, verify, fix, re-verify until all specs pass.

## Key principles

- **Spec is primary.** Code is derived. When in doubt, fix the spec first.
- **Specs are concise.** If you can't read a spec in 30 seconds, split the feature.
- **Examples are contracts.** They define correctness, not just documentation.
- **Collaborative evolution.** Both human and LLM propose changes. Human approves.
- **Verify, don't trust.** Always run verification after generating or modifying implementations.
