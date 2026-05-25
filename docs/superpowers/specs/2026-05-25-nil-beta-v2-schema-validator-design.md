# nil-beta v2: Schema-level API contract validator

## Summary

nil-beta becomes a language-agnostic API contract validator. Feature specs define typed interfaces (Malli schemas for inputs/outputs). System specs declare which tech stack implements each feature and how components connect. nil validates that contracts are internally consistent and compatible at connection boundaries. No code execution, no runtime dispatch.

## Motivation

nil-beta v1 built a Babashka runtime (dispatch, validation, verification) that could only run Clojure implementations. This limits nil to one language and couples it to code execution. The real value is the spec language itself -- it defines the system's API surface regardless of implementation language.

By stripping the runtime and focusing on schema validation, nil becomes useful for any tech stack: Python, TypeScript, Go, REST APIs, CLI tools. The spec stays the single source of truth. The harness generates implementations in whatever language the system spec declares.

## Feature specs

Feature specs define typed interfaces. Each case has an input schema and output schema using Malli:

```clojure
;; nil/features/translate.clj
{:id :translate
 :desc "Translate natural-language queries into computable forms"
 :cases
 {:translate
  {:input  [:map [:query :string]]
   :output [:map [:expression :string]]
   :desc   "Convert natural language to a Clojure expression"
   :examples [{:in {:query "two plus two"} :out {:expression "(+ 2 2)"}}]}}}
```

### Changes from v1

- Inputs/outputs are plain maps or values, not tuples with dispatch tags. The `[:tuple [:= :tag] ...]` convention was a runtime dispatch concern and is removed.
- Cases are still named for documentation and generation context, but no dispatch machinery exists.

### Feature spec fields

| Field    | Required | Description |
|----------|----------|-------------|
| `:id`    | yes      | Unique keyword identifying the feature |
| `:desc`  | no       | Natural-language description |
| `:deps`  | no       | Vector of feature `:id`s this feature depends on |
| `:cases` | yes      | Map of case names to case definitions |

Each case:

| Field       | Required | Description |
|-------------|----------|-------------|
| `:input`    | yes      | Malli schema for the input |
| `:output`   | yes      | Malli schema for the output |
| `:desc`     | no       | Case-level description |
| `:examples` | no       | `[{:in <value> :out <value>}]` -- must conform to input/output schemas |

## System specs

A system declares components, their implementation language, and how they connect:

```clojure
;; nil/systems/calculator.clj
{:id :calculator
 :desc "Natural language calculator"
 :components
 {:translate {:feature :translate :lang :python}
  :compute   {:feature :compute   :lang :babashka}
  :ui        {:feature :ui        :lang :typescript}}
 :connections
 [[:translate :translate :output] [:compute :eval :input]]}
```

### System spec fields

| Field          | Required | Description |
|----------------|----------|-------------|
| `:id`          | yes      | Unique keyword identifying the system |
| `:desc`        | no       | Natural-language description |
| `:components`  | yes      | Map of component names to `{:feature <id> :lang <keyword>}` |
| `:connections`  | no       | Vector of connection pairs: `[<from> <to>]` where each is `[<component> <case> :input/:output]` |

### Connection format

A connection is a pair of endpoints: `[<source-endpoint> <sink-endpoint>]`.

Each endpoint is `[<component-name> <case-name> :output]` or `[<component-name> <case-name> :input]`.

Example: `[[:translate :translate :output] [:compute :eval :input]]` means "the output of translate's :translate case feeds into compute's :eval case input."

### `:lang` values

`:lang` is a free-form keyword. Common values: `:babashka`, `:clojure`, `:python`, `:typescript`, `:go`, `:rust`, `:rest-api`, `:cli`. nil does not interpret `:lang` -- it's metadata for the harness and skill to use during generation.

## Validation

nil performs three levels of validation. No code is executed.

### 1. Spec validity

Each feature and system spec is well-formed per its Malli schema. This is the existing `validate-spec!` logic, extended for system specs.

### 2. Example conformance

Each example's `:in` value must conform to the case's `:input` schema. Each example's `:out` value must conform to the case's `:output` schema. This catches specs where the examples don't match the declared types.

### 3. Connection compatibility

For each connection in a system spec, the source endpoint's schema must be compatible with the sink endpoint's schema. Specifically: any value that conforms to the output schema of the source must also conform to the input schema of the sink.

In practice, this means checking that the output schema is a subtype of (or equal to) the input schema. Malli doesn't have built-in subtype checking, so the pragmatic approach is:

1. Generate sample values from the output schema using `malli.generator/generate`
2. Validate each against the input schema
3. If any generated value fails input validation, the connection is incompatible

This is a heuristic (not a proof), but it catches most real mismatches with zero false positives for compatible schemas.

## File structure

### nil-beta project

```
src/nil/
  spec.clj          Feature + system spec schemas, validate-spec!
  validate.clj      Example conformance, connection compatibility
  core.clj          Public API: load-spec, load-system, validate, validate-all
test/nil/
  spec_test.clj
  validate_test.clj
  core_test.clj
skills/nil-beta/
  SKILL.md
```

### Deployed to target repo

```
nil/
  core.clj
  spec.clj
  validate.clj
  features/         Feature spec files (EDN)
  systems/          System spec files (EDN)
```

No `generated/` directory -- nil doesn't own implementations anymore. Generated code lives wherever the target project's conventions put it (e.g. `src/`, `lib/`, etc.).

## What gets cut from v1

| v1 component | Disposition |
|--------------|-------------|
| `registry.clj` | Cut -- no impl registration needed |
| `runtime.clj` | Cut -- no dispatch, no runtime validation |
| `verify.clj` | Cut -- no execution against implementations |
| `init.clj` | Cut for now -- simplify once core stabilizes |
| Tuple input convention `[:tag & args]` | Cut -- inputs are plain schemas |
| `generated/` directory | Cut -- nil doesn't own impl files |

## What stays

| Component | Status |
|-----------|--------|
| `spec.clj` | Kept, extended with system spec schema |
| `core.clj` | Kept, simplified (load + validate only) |
| Malli schemas | Kept -- core of the spec language |
| `:deps` on feature specs | Kept |
| `:examples` on cases | Kept -- validated against schemas |
| Skill | Kept, updated for language-agnostic model |

## What's new

| Component | Description |
|-----------|-------------|
| System specs | Declare components, languages, and connections |
| `validate.clj` | Example conformance + connection compatibility checking |
| Connection validation | Malli-generator-based compatibility heuristic |

## The skill (updated role)

The skill teaches the harness:
- How to read feature and system specs
- How to author/evolve specs collaboratively
- How to generate implementations in the target language (guided by `:lang` from the system spec)
- How to run nil validation to check contracts
- Integration with superpowers and ralph loop using specs as contracts

The skill no longer describes Clojure-specific implementation conventions (namespace shape, `-impl` function, `register-impl!`). Instead it explains the contract each implementation must satisfy (accept inputs matching the schema, produce outputs matching the schema) and leaves language-specific conventions to the harness's judgment.

## Open questions

- **Malli generator availability in Babashka:** `malli.generator` requires `test.check`. Need to verify this works in Babashka or find an alternative for connection validation.
- **Subtype checking depth:** The generate-and-validate heuristic may miss edge cases with complex schemas. Acceptable for v2; can be strengthened later.
