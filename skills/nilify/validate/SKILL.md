---
name: nilify:validate
description: Validate nilify spec consistency -- spec well-formedness, example conformance against schemas, and connection compatibility between systems. Use when checking specs after authoring or before generation.
---

# nilify:validate

Run schema-level validation on nilify specs. No code is executed.

## When to use this skill

- After authoring or modifying specs (invoked by `nilify:author`)
- Before generating implementations (invoked by `nilify:generate`)
- User asks to "validate", "check", or "verify" the specs
- CI/pre-commit check

## Three levels of validation

### 1. Spec validity

Each feature and system spec is well-formed per its Malli schema.

```bash
bb -e '(require (quote nil.core)) (nil.core/load-spec "nil/features/compute.clj")'
```

Catches: missing `:id`, missing `:cases`, malformed schemas, invalid `:provides` structure.

### 2. Example conformance

Each example's `:in` value conforms to the case's `:input` schema. Each `:out` value conforms to the `:output` schema.

```bash
bb -e '(require (quote nil.core)) (clojure.pprint/pprint (nil.core/validate {:features [(nil.core/load-spec "nil/features/compute.clj")]}))'
```

Catches: examples that don't match their declared types.

### 3. Connection compatibility

For each connection in a system spec, the source output schema is compatible with the sink input schema. Uses `malli.generator` to sample values from the output schema and validate against the input schema.

```bash
bb -e '(require (quote nil.core)) (clojure.pprint/pprint (nil.core/validate-all "nil/features" "nil/systems"))'
```

Catches: systems wired together with incompatible schemas.

## Reporting

Present results concisely:

```
Validation results:
  ✓ spec validity: 5/5 specs valid
  ✓ example conformance: 8/8 examples pass
  ✗ connection compatibility: 1 failure
    [:sys/frontend :feat/search-ui :output] → [:sys/backend :feat/api :input]
    output schema [:map [:query :int]] incompatible with input [:map [:query :string]]
```

## When validation fails

- **Spec validity failure** -- fix the spec structure. Invoke `nilify:author` if the user needs help.
- **Example conformance failure** -- either the example or the schema is wrong. Ask the user which to fix.
- **Connection incompatibility** -- the systems disagree on the interface contract. The provider's `:provides` schema is the source of truth. Fix the consumer's expectations or update the provider.
