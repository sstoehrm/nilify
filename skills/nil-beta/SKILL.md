---
name: nil-beta
description: Language-agnostic API contract validation. Author feature specs, define systems with tech stacks, validate schema compatibility. Use when working in a repo with a nil/ folder.
---

# nil-beta: API Contract Validator

You are working in a repository that uses **nil-beta** -- a language-agnostic API contract validator. The `nil/` folder defines the system's API surface through specs. Specs are the primary artifact; implementations are derived in whatever language the system declares.

## When to use this skill

- The user asks to create, modify, or review a nil spec
- The user asks to define a system or its components
- The user asks to validate contracts or check schema compatibility
- You see a `nil/` folder with `features/` or `systems/` subdirectories
- The user references "nil", "nil-beta", or "specs" in the context of API design

## Understanding nil/

```
nil/
  core.clj          Public API: load-spec, load-system, validate, validate-all
  spec.clj          Feature + system spec schemas (Malli)
  validate.clj      Example conformance + connection compatibility checking
  features/         Feature spec files (EDN, one per feature)
    translate.clj
    compute.clj
  systems/          System spec files (EDN, one per system)
    calculator.clj
```

## Feature specs

Feature specs define typed interfaces. No language, no runtime -- just contracts:

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

### Fields

- `:id` (required) -- unique keyword
- `:desc` -- natural-language description
- `:deps` -- vector of feature ids this feature depends on
- `:cases` -- map of case names to `{:input <schema> :output <schema> :desc <string> :examples [{:in ... :out ...}]}`

Schemas use Malli syntax: `:string`, `:int`, `:double`, `[:map [:key :type] ...]`, `[:vector :type]`, etc.

## System specs

A system declares which features exist, what tech stack implements each, and how they connect:

```clojure
;; nil/systems/calculator.clj
{:id :calculator
 :desc "Natural language calculator"
 :components
 {:translate {:feature :translate :lang :python}
  :compute   {:feature :compute   :lang :babashka}
  :ui        {:feature :ui        :lang :typescript}}
 :connections
 [[[:translate :translate :output] [:compute :eval :input]]]}
```

### Fields

- `:id` (required) -- unique keyword
- `:desc` -- natural-language description
- `:components` -- map of component names to `{:feature <id> :lang <keyword>}`
- `:connections` -- vector of `[[<component> <case> :output] [<component> <case> :input]]` pairs

`:lang` is a free-form keyword (`:python`, `:typescript`, `:go`, `:babashka`, `:rest-api`, etc.). nil doesn't interpret it -- it's metadata for you to use when generating implementations.

## Validation

nil validates three things without executing any code:

1. **Spec validity** -- feature and system specs are well-formed
2. **Example conformance** -- each example's `:in` matches its `:input` schema, each `:out` matches its `:output` schema
3. **Connection compatibility** -- where a system wires output A to input B, values conforming to A's output schema also conform to B's input schema

Run validation:

```bash
bb -e '(require (quote nil.core)) (clojure.pprint/pprint (nil.core/validate-all "nil/features" "nil/systems"))'
```

## Spec authoring

When authoring a new feature spec:

1. **Discuss intent** -- what does this component do?
2. **Define schemas** using Malli for inputs and outputs
3. **Add examples** -- concrete pairs that conform to the schemas
4. **Add `:deps`** if this feature depends on others
5. **Write to** `nil/features/<id>.clj`

When authoring a system spec:

1. **List components** with their feature refs and target languages
2. **Define connections** showing data flow between components
3. **Run validation** to check schema compatibility
4. **Write to** `nil/systems/<id>.clj`

## Generating implementations

When generating an implementation for a feature:

1. Read the feature spec from `nil/features/<id>.clj`
2. Check the system spec to find the target `:lang` for this component
3. Generate the implementation in that language, ensuring it accepts inputs matching `:input` schemas and produces outputs matching `:output` schemas
4. Place the implementation wherever the target project's conventions dictate

nil does not own generated code. It only validates contracts.

## Integration with other skills

### With superpowers:brainstorming
Use nil specs as the output of brainstorming. Define the API surface before writing code.

### With superpowers:subagent-driven-development
Each feature spec is a natural task boundary. One subagent per feature to generate implementations.

### With ralph-loop
Use nil validation as the success criteria. Ralph iterates until all schemas pass.

## Key principles

- **Spec is primary.** Code is derived. Fix the spec before fixing the code.
- **Language-agnostic.** Specs define contracts, not implementations.
- **Validate, don't execute.** nil checks schemas, not behavior.
- **Collaborative evolution.** Both human and LLM propose spec changes. Human approves.
