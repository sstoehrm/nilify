# drill.trace -- Leveled observability for generation and produce

## Summary

Add a `drill.trace` namespace that emits structured diagnostic events at each stage of the generation and produce paths. Controlled by a `*level*` dynamic var (`:off` / `:info` / `:debug`) and exposed via `--verbose` / `--debug` CLI flags.

## Motivation

Currently drill is silent during generation and produce calls. Long-running LLM calls give no feedback, prompts and responses are invisible, and validation happens without any trace. Users have no way to understand what drill is doing, debug prompt issues, or measure timing.

## Design

### drill.trace namespace

Two public things:

- `*level*` -- dynamic var. Values: `:off` (default), `:info`, `:debug`. Level ordering: `:off` < `:info` < `:debug`.
- `(emit level event-map)` -- prints to stderr when the event's level is at or below `*level*`. No-ops otherwise.

### Output format

`:info` events print one human-readable line to stderr:

```
[drill/info] prompt-built :compute (1823 chars)
[drill/info] llm-call-start :compute
[drill/info] llm-call-done :compute (3241ms, 892 chars)
[drill/info] file-written :compute -> drill_generated/compute.clj
[drill/info] smoke-pass :compute (2 examples)
```

`:debug` events print the line plus the full event map pretty-printed below:

```
[drill/debug] prompt-built :compute (1823 chars)
  {:id :compute, :prompt "You are generating...", :prompt-length 1823}
```

### Instrumented stages

#### Generation path (generator/gen-feature!)

| Stage | Level | Key fields |
|-------|-------|------------|
| prompt-built | :info | `:id`, `:prompt-length` |
| prompt-built | :debug | adds `:prompt` (full text) |
| llm-call-start | :info | `:id` |
| llm-call-done | :info | `:id`, `:elapsed-ms`, `:response-length` |
| llm-call-done | :debug | adds `:response` (full text) |
| block-extracted | :debug | `:id`, `:block-length` |
| file-written | :info | `:id`, `:path`, `:spec-hash` |
| smoke-pass | :info | `:id`, `:example-count` |

#### Produce path (produce/call)

| Stage | Level | Key fields |
|-------|-------|------------|
| produce-prompt-built | :info | `:id`, `:tag`, `:prompt-length` |
| produce-prompt-built | :debug | adds `:prompt` |
| produce-llm-call-start | :info | `:id`, `:tag` |
| produce-llm-call-done | :info | `:id`, `:tag`, `:elapsed-ms` |
| produce-llm-call-done | :debug | adds `:response` |
| produce-result | :debug | `:id`, `:tag`, `:result` |

#### Validation (runtime + produce)

| Stage | Level | Key fields |
|-------|-------|------------|
| input-validated | :debug | `:id`, `:tag`, `:input` |
| output-validated | :debug | `:id`, `:tag`, `:output` |

### CLI integration

Two new flags parsed in `core/main`:

- `--verbose` -- binds `*level*` to `:info`
- `--debug` -- binds `*level*` to `:debug`

These compose with existing flags: `bb my-app.clj --generate --verbose`.

### Formatting logic

`emit` receives a level and an event map. The event map always contains `:stage` (keyword). The `emit` function:

1. Checks if the event level is enabled (level <= *level*). If not, returns nil.
2. Builds a one-line summary from `:stage` and key fields (varies per stage).
3. Prints `[drill/<level>] <summary>` to stderr.
4. When `*level*` is `:debug`, also pretty-prints the full event map indented below.

The formatting of the one-line summary is handled by a `format-summary` multimethod or cond on `:stage`. This keeps the emit function itself simple.

### Testing

- `*level*` defaults to `:off` so all existing tests are unaffected with zero changes.
- New `drill.trace_test` captures stderr via `with-out-str` on `*err*` and asserts events appear at correct levels.
- Tests verify: events emit at `:info`, events emit at `:debug`, events suppressed at `:off`, format strings are correct.

## Files changed

- **New:** `src/drill/trace.clj` -- the trace namespace
- **Modified:** `src/drill/generator.clj` -- emit events at each stage of `gen-feature!`
- **Modified:** `src/drill/produce.clj` -- emit events at each stage of `call`
- **Modified:** `src/drill/runtime.clj` -- emit validation events in `dispatch`
- **Modified:** `src/drill/core.clj` -- parse `--verbose`/`--debug` flags, bind `*level*`
- **New:** `test/drill/trace_test.clj` -- tests for trace namespace
- **Modified:** `test/runner.clj` -- include trace_test

## Out of scope

- Pluggable trace consumers (callback fn). Can be added later by making `emit` call through a dynamic var.
- File-based logging.
- Metrics collection or aggregation.
