# Design: sci-evaluated tree specs

**Date:** 2026-05-29
**Status:** Approved (pending written-spec review)
**Branch:** `tree-spec-sci-eval`
**Resolves:** GitHub issue [#6](https://github.com/sstoehrm/nilify/issues/6) parts 1 & 2 (part 3 already fixed)

## Problem

nilify documents two incompatible spec formats:

- **Flat EDN** — bare maps in `nil/features/*.clj` and `nil/systems/*.clj` with `:cases` / `:components` / `:connections`. This is the only format `nilify validate` supports (it `edn/read-string`s one form per file and checks it against the `FeatureSpec` / `SystemSpec` Malli schemas).
- **Tree** — the README Quick example, `nilify spec` §3, and both `examples/*.clj`: a Clojure script using `nilify/root` and `nilify/prompt`, composing `:system` / `:subsystem` / `:layer` / `:feature` nodes with `:provides` / `:connects-to` / `:internals`. This format has **no Malli schema and no loader**, and `nilify.core` does not exist.

Issue #6 parts 1 & 2 are symptoms of this gap: a user wrote a tree-style file into `nil/systems/`, and `validate` choked because (a) `edn/read-string` reads only the leading `(ns …)` form, and (b) `(nilify/prompt …)` and shared `def`'d schema vars are not EDN — they require *evaluation*, which the loader never does.

The two formats also model different things — the flat model is a typed-contract checker over feature cases; the tree model is an architecture/interface description. They are not two syntaxes for one model.

## Decision

Make the **tree the single canonical model**, and make `nilify validate` **evaluate** the spec file (with babashka's bundled sci) instead of `edn/read-string`-ing it. Because the file is executed:

- `(nilify/prompt …)` runs and returns a string,
- shared schema vars (e.g. `spec-todo`) resolve,
- the `(ns …)` wrapper is irrelevant — we take the value the file evaluates to.

That alone resolves #6 parts 1 & 2. The flat model is retired.

Spec files now end with `(nilify/root [...])` as their **last form**, so evaluating the file *returns the tree*. (Both examples were already updated to this shape.)

## Components

### 1. `nilify.core` runtime (defined in-process inside the CLI)

Two functions, defined in the CLI script so an evaluated spec's `(:require [nilify.core :as nilify])` resolves to the already-loaded namespace:

- `prompt` — `(defn prompt [& parts] (clojure.string/join " " parts))`. Joins string fragments into one description string.
- `root` — `(defn root [systems] systems)`. Returns the `[system+]` vector as the normalized tree. Pure constructor; it does **not** validate (that is `validate`'s job).

This is the "runtime library" open-work item, reduced to its minimum: in the tree, only `prompt` and `root` are functions — every node is a plain vector literal and every schema is plain Malli data.

### 2. Loader

```clojure
(defn load-tree [path]
  (load-string (slurp path)))   ; last form (nilify/root …) → returns the tree
```

Wrapped in try/catch to surface evaluation failures as a clean message (this also removes #6 part 1's raw `ExceptionInfo` stacktrace). Uses babashka's bundled sci — no new dependency.

> **Sandboxing (deferred):** specs are the user's own files, so full evaluation is acceptable for now. If isolation is wanted later, the fallback is a real `sci.core` context binding only `nilify.core` plus a safe builtin subset. This does not change the design and is confirmed via a quick spike at implementation time.

### 3. Tree schema (new Malli schemas; replace `FeatureSpec` / `SystemSpec`)

```
root       → [:vector SystemNode]
SystemNode → [:system {:id :tech? :desc? :provides? :connects-to?} (LayerNode+ | SubsystemNode+)]
               :id          :sys/<name>  (required)
               :provides    [:vector iface-kw]            ; interface NAMES only
               :connects-to #{[sys-id iface-id] …}
SubsystemNode → [:subsystem {:id :uses? :provides?} LayerNode+]
               :id          :sub/<name>  (required)
               :uses        [:vector sub-id]              ; sibling subsystems
               :provides    {route {:interface iface-kw :input <schema> :output <schema>}}  ; typed definitions
LayerNode  → [:layer FeatureNode+]                        ; ordered TOP-FIRST (see below)
FeatureNode → [:feature {:id :tech? :desc? :internals?}]
               :id          :feat/<name>  (required)
               :internals   <free-form map>
```

A system's children are **either all layers or all subsystems** — not a mix (simple systems use layers directly, e.g. the frontend; composed systems use subsystems, e.g. the backend). This is enforced in level-1 structural validation. A system advertises interfaces by name in `:provides`; the typed route definitions live in the subsystem's `:provides`, tagged with the matching `:interface`.

**Layer ordering is top-first.** The first `:layer` in the vector is the topmost (most-exposed/usable) layer; each later layer is lower and more foundational; dependencies flow *downward* (a layer may depend on layers listed after it). This matches both examples (`:feat/api` / `:feat/ui` appear first; `:feat/domain-model` last) and the "only the upper layer is usable" rule. **This corrects the old README/`spec` wording of "bottom-to-top, foundational first," which is inverted relative to the examples** — the README and spec reference will be updated to top-first.

A subsystem's `:provides` *route key* is free-form (method + path, e.g. `["HTTP GET" ["/"]]`); only the route *value* `{:interface :input :output}` is validated strictly.

### 4. Validation levels (redefined for the tree)

All schema-level; no project code executed (beyond evaluating the spec file itself).

1. **Structural** — the whole tree conforms to the schema above: node types, required `:id`s, `:provides` shape, `:connects-to` shape.
2. **Reference & visibility integrity:**
   - every `:connects-to [sys iface]` → `sys` exists in the tree and lists `iface` in its `:provides`;
   - every interface a system advertises in `:provides` is *defined* by ≥1 route in one of that system's subsystems (route `:interface` matches), and no subsystem route advertises an interface its enclosing system does not list — the declaration↔definition link;
   - every `:uses [sub]` resolves to a sibling subsystem within the same system;
   - all `:sys/` / `:sub/` / `:feat/` ids are unique across the tree.
3. **Type checks** — each provided route's `:input` / `:output` is a valid, generatable Malli schema; any `:examples` carried by routes/features conform to their schemas.

**Removed:** the old pairwise connection type-compatibility check (sample producer `:output`, validate against consumer `:input`). In the tree, `:connects-to` references an interface *by name only* — the consumer declares no schema, so there is no second side to sample. Replaced by the well-formedness/generatability check in level 3. Restoring a two-sided check would require consumers to declare an expected interface shape; noted as future work.

**Visibility ("only the upper layer is usable"):** modeled as a principle that informs generation — lower layers are private implementation of a subsystem; only the top layer is exposed to consumers. The only part currently *enforceable* (and enforced) is "`:uses` resolves to a sibling subsystem," because references are coarse (a subsystem id), never a path into a specific layer/feature. Finer enforcement is future work.

## File layout & commands

- **Spec location:** a single root spec file per project (the model is "one root per project"). `nilify validate` evaluates `nil/root.clj` by default, overridable with `nilify validate [path]`.
- **`nilify init`:** creates `nil/` and writes a minimal starter `nil/root.clj` (a `(nilify/root [...])` template). Stops creating `nil/features/` and `nil/systems/`. Skill download is unchanged.
- **`nilify validate`:** evaluates the root file, runs the three levels, prints per-level pass/fail, exits non-zero on any failure.

## Conventions

- Subsystem ids use the **`:sub/`** prefix (standardized; `:subsys/` is dropped).
- Naming table everywhere gains `:sub/`.

## Impact / work items

- **CLI (`nilify`):** add `nilify.core` ns (`prompt`, `root`); replace `load-spec`/`load-system`/`load-dir`/`validate-all` with `load-tree` + tree validators; remove `FeatureSpec`/`SystemSpec`/`Case`/`Component`/`Endpoint`/`Connection`/`check-examples`/`check-connections` in favor of tree-schema + level-2/3 checks; update `cmd-init`, `cmd-validate`; rewrite the embedded `spec-reference` string (drop flat §1/§2; document the tree, subsystems, `:provides` split, `:uses`, `:internals`, `prompt`). Bump `version` `0.1.0` → `0.2.0`.
- **Tests (`test/nilify_test.clj`):** rewrite the suite against the new functions — runtime (`prompt`/`root`), `load-tree` on a temp file returning a tree, structural validity (valid passes / malformed fails), reference integrity (missing system/iface/subsystem fail; provides declaration↔definition mismatch fails), type checks, and a `nilify validate` subprocess integration test on a temp `nil/root.clj`.
- **README:** update the Quick example (ends with `(nilify/root …)`, includes a subsystem), the Spec tree section (add `subsystem`, `:uses`, `:provides` name-list vs typed routes), the Validation section (new level semantics), and the naming table (`:sub/`).
- **Skills:** `nilify-author` (drop the `nilc/prompt`-for-flat guidance; describe tree authoring, subsystems, the `:provides` declaration/definition split, visibility, `prompt`); `nilify-validate` (update the three-level descriptions and sample output to the new semantics — invocation already fixed to `nilify validate`); review `nilify`, `nilify-extract`, `nilify-onboard`, `nilify-diff`, `nilify-generate` for flat-model references.
- **`.claude/skills/working-on-nilify/SKILL.md`:** update the spec-language section (node types, `:sub/`, single-root-file layout) and remove the "runtime library not implemented" gap (now implemented).
- **Examples:** already in canonical shape; verify they validate once the CLI lands.

## Out of scope (future work)

- `nilify diff` and `nilify generate` CLI commands.
- Sandboxed `sci.core` context (full eval for now).
- Consumer-side typed interface expectations → restoring two-sided connection type-compat.
- Finer cross-subsystem visibility enforcement.
- Spec-tree visualization.

## Verification

`bb test/runner.clj` green (rewritten suite); `bb nilify validate examples/todo.clj` and `examples/easy-calc.clj` both pass; `nilify spec` prints the tree reference; a fresh `nilify init` produces a `nil/root.clj` that validates.
