# drill.trace Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add leveled observability (`*level*` = `:off` / `:info` / `:debug`) to drill's generation and produce paths, with `--verbose` and `--debug` CLI flags.

**Architecture:** A new `drill.trace` namespace owns the `*level*` dynamic var and an `emit` function that prints structured events to stderr. The generator, produce, and runtime namespaces call `emit` at each stage. `core/main` parses CLI flags to bind `*level*`.

**Tech Stack:** Clojure/Babashka, clojure.pprint, clojure.test

**Spec:** `docs/superpowers/specs/2026-05-23-drill-trace-design.md`

---

### File map

| File | Action | Responsibility |
|------|--------|----------------|
| `src/drill/trace.clj` | Create | `*level*`, `emit`, `format-summary` |
| `src/drill/generator.clj` | Modify | Emit events in `gen-feature!` |
| `src/drill/produce.clj` | Modify | Emit events in `call` |
| `src/drill/runtime.clj` | Modify | Emit validation events in `dispatch` |
| `src/drill/core.clj` | Modify | Parse `--verbose`/`--debug`, bind `*level*` |
| `test/drill/trace_test.clj` | Create | Tests for trace namespace |
| `test/runner.clj` | Modify | Add `drill.trace-test` to test list |

---

### Task 1: drill.trace namespace -- core emit logic

**Files:**
- Create: `src/drill/trace.clj`
- Create: `test/drill/trace_test.clj`
- Modify: `test/runner.clj`

- [ ] **Step 1: Write failing tests for emit and level gating**

Create `test/drill/trace_test.clj`:

```clojure
(ns drill.trace-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [drill.trace :as trace]))

(defn- capture-stderr [f]
  (let [sw (java.io.StringWriter.)]
    (binding [*err* sw]
      (f))
    (str sw)))

(deftest emit-off-suppresses-all
  (testing "events are suppressed when *level* is :off"
    (let [out (capture-stderr
               #(binding [trace/*level* :off]
                  (trace/emit :info {:stage :prompt-built :id :test :prompt-length 100})))]
      (is (= "" out)))))

(deftest emit-info-shows-info-events
  (testing "info events appear when *level* is :info"
    (let [out (capture-stderr
               #(binding [trace/*level* :info]
                  (trace/emit :info {:stage :prompt-built :id :test :prompt-length 100})))]
      (is (str/includes? out "[drill/info]"))
      (is (str/includes? out ":test"))
      (is (str/includes? out "100")))))

(deftest emit-info-hides-debug-events
  (testing "debug events are hidden when *level* is :info"
    (let [out (capture-stderr
               #(binding [trace/*level* :info]
                  (trace/emit :debug {:stage :block-extracted :id :test :block-length 50})))]
      (is (= "" out)))))

(deftest emit-debug-shows-full-map
  (testing "debug level prints the full event map"
    (let [out (capture-stderr
               #(binding [trace/*level* :debug]
                  (trace/emit :debug {:stage :block-extracted :id :test :block-length 50})))]
      (is (str/includes? out "[drill/debug]"))
      (is (str/includes? out ":block-length")))))

(deftest emit-debug-shows-info-events
  (testing "info events also appear at debug level"
    (let [out (capture-stderr
               #(binding [trace/*level* :debug]
                  (trace/emit :info {:stage :llm-call-start :id :test})))]
      (is (str/includes? out "[drill/info]"))
      (is (str/includes? out ":test")))))

(deftest format-summary-per-stage
  (testing "each stage produces a meaningful summary line"
    (let [cases [{:stage :prompt-built :id :x :prompt-length 500}
                 {:stage :llm-call-start :id :x}
                 {:stage :llm-call-done :id :x :elapsed-ms 1234 :response-length 800}
                 {:stage :file-written :id :x :path "drill_generated/x.clj" :spec-hash "abc"}
                 {:stage :smoke-pass :id :x :example-count 3}
                 {:stage :produce-prompt-built :id :x :tag :translate :prompt-length 200}
                 {:stage :produce-llm-call-start :id :x :tag :translate}
                 {:stage :produce-llm-call-done :id :x :tag :translate :elapsed-ms 500}
                 {:stage :produce-result :id :x :tag :translate :result "ok"}
                 {:stage :input-validated :id :x :tag :eval :input [:eval "1"]}
                 {:stage :output-validated :id :x :tag :eval :output 1.0}]]
      (doseq [evt cases]
        (let [out (capture-stderr
                   #(binding [trace/*level* :info]
                      (trace/emit :info evt)))]
          (is (str/includes? out (name (:stage evt)))
              (str "missing stage name in output for " (:stage evt))))))))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `bb test/runner.clj`

Expected: Failure -- `drill.trace` namespace does not exist.

- [ ] **Step 3: Add trace_test to runner.clj**

In `test/runner.clj`, add `drill.trace-test` to the `test-namespaces` vector, after `drill.core-test` and before `end-to-end-test`:

```clojure
(def test-namespaces
  '[drill.spec-test
    drill.registry-test
    drill.runtime-test
    drill.drift-test
    drill.prompt-test
    drill.generator-test
    drill.produce-test
    drill.core-test
    drill.trace-test
    end-to-end-test])
```

- [ ] **Step 4: Implement drill.trace**

Create `src/drill/trace.clj`:

```clojure
(ns drill.trace
  (:require [clojure.pprint :as pp]))

(def ^:dynamic *level* :off)

(def ^:private level-rank {:off 0 :info 1 :debug 2})

(defn- enabled? [event-level]
  (>= (level-rank *level* 0) (level-rank event-level 0)))

(defn- format-summary [{:keys [stage id tag prompt-length elapsed-ms
                                response-length block-length path
                                spec-hash example-count]}]
  (case stage
    :prompt-built
    (str "prompt-built " id " (" prompt-length " chars)")

    :llm-call-start
    (str "llm-call-start " id)

    :llm-call-done
    (str "llm-call-done " id " (" elapsed-ms "ms, " response-length " chars)")

    :block-extracted
    (str "block-extracted " id " (" block-length " chars)")

    :file-written
    (str "file-written " id " -> " path)

    :smoke-pass
    (str "smoke-pass " id " (" example-count " examples)")

    :produce-prompt-built
    (str "produce-prompt-built " id "/" tag " (" prompt-length " chars)")

    :produce-llm-call-start
    (str "produce-llm-call-start " id "/" tag)

    :produce-llm-call-done
    (str "produce-llm-call-done " id "/" tag " (" elapsed-ms "ms)")

    :produce-result
    (str "produce-result " id "/" tag)

    :input-validated
    (str "input-validated " id "/" tag)

    :output-validated
    (str "output-validated " id "/" tag)

    (str (name stage) " " id)))

(defn emit [level event]
  (when (enabled? level)
    (let [summary (format-summary event)]
      (binding [*out* *err*]
        (println (str "[drill/" (name level) "] " summary))
        (when (= *level* :debug)
          (println (str "  " (with-out-str (pp/pprint event)))))
        (flush)))))
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `bb test/runner.clj`

Expected: All tests pass including the new `drill.trace-test` tests.

- [ ] **Step 6: Commit**

```bash
git add src/drill/trace.clj test/drill/trace_test.clj test/runner.clj
git commit -m "feat(trace): add drill.trace namespace with leveled emit"
```

---

### Task 2: Instrument the generation path

**Files:**
- Modify: `src/drill/generator.clj`

- [ ] **Step 1: Write failing test for generation trace events**

Add to `test/drill/trace_test.clj`:

```clojure
(deftest gen-feature-emits-trace-events
  (testing "gen-feature! emits expected trace events at :info level"
    (let [events (atom [])
          spec {:id :traced
                :kind :feature
                :lang :babashka
                :desc "test"
                :cases {:eval {:input [:tuple [:= :eval] :string]
                               :output :double
                               :examples [{:in [:eval "(+ 1 2)"] :out 3.0}]}}}]
      (binding [trace/*level* :info
                gen/*generated-dir* "test/tmp"
                gen/*llm-call* (fn [_]
                                 (str "```clojure\n"
                                      "(ns drill-generated.traced\n"
                                      "  (:require [drill.registry :as reg]))\n"
                                      "(defn -impl [[tag & args]]\n"
                                      "  (case tag :eval (Double/parseDouble (str (load-string (first args))))))\n"
                                      "(reg/register-impl! :traced -impl)\n"
                                      "```"))]
        (let [out (capture-stderr
                   #(do (reg/clear!)
                        (reg/register-spec! spec)
                        (gen/gen-feature! spec)))]
          (is (str/includes? out "prompt-built"))
          (is (str/includes? out "llm-call-start"))
          (is (str/includes? out "llm-call-done"))
          (is (str/includes? out "file-written"))
          (is (str/includes? out "smoke-pass"))
          (reg/clear!))))))
```

Add the required requires to the ns form:

```clojure
(ns drill.trace-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [drill.trace :as trace]
            [drill.generator :as gen]
            [drill.registry :as reg]))
```

- [ ] **Step 2: Run tests to verify the new test fails**

Run: `bb test/runner.clj`

Expected: `gen-feature-emits-trace-events` fails -- no trace output from generator yet.

- [ ] **Step 3: Add trace emit calls to generator.clj**

Add `[drill.trace :as trace]` to the require vector in `generator.clj`.

Replace the body of `gen-feature!` with:

```clojure
(defn gen-feature! [spec]
  (let [siblings    (->> (vals @reg/descriptions)
                         (remove #(= (:id %) (:id spec))))
        prompt-text (prompt/build spec siblings)]
    (trace/emit :info {:stage :prompt-built :id (:id spec) :prompt-length (count prompt-text)})
    (trace/emit :debug {:stage :prompt-built :id (:id spec) :prompt-length (count prompt-text) :prompt prompt-text})
    (trace/emit :info {:stage :llm-call-start :id (:id spec)})
    (let [start    (System/currentTimeMillis)
          response (*llm-call* prompt-text)
          elapsed  (- (System/currentTimeMillis) start)]
      (trace/emit :info {:stage :llm-call-done :id (:id spec) :elapsed-ms elapsed :response-length (count response)})
      (trace/emit :debug {:stage :llm-call-done :id (:id spec) :elapsed-ms elapsed :response-length (count response) :response response})
      (let [body    (extract-block response)
            _       (trace/emit :debug {:stage :block-extracted :id (:id spec) :block-length (count body)})
            h       (drift/spec-hash spec)
            content (str (header h) body)
            path    (id->path (:id spec))]
        (io/make-parents path)
        (spit path content)
        (trace/emit :info {:stage :file-written :id (:id spec) :path path :spec-hash h})
        (smoke! spec path)
        (let [example-count (->> (:cases spec) vals (mapcat :examples) count)]
          (trace/emit :info {:stage :smoke-pass :id (:id spec) :example-count example-count}))
        {:id (:id spec) :status :generated :path path}))))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `bb test/runner.clj`

Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/drill/generator.clj test/drill/trace_test.clj
git commit -m "feat(trace): instrument generation path with trace events"
```

---

### Task 3: Instrument the produce path

**Files:**
- Modify: `src/drill/produce.clj`

- [ ] **Step 1: Write failing test for produce trace events**

Add to `test/drill/trace_test.clj`:

```clojure
(deftest produce-call-emits-trace-events
  (testing "produce/call emits expected trace events at :info level"
    (let [spec {:id :translate
                :kind :produce
                :desc "Translate"
                :cases {:translate {:input [:tuple [:= :translate] :string]
                                    :output :string
                                    :examples [{:in [:translate "hi"] :out "hello"}]}}}]
      (binding [trace/*level* :info
                gen/*llm-call* (fn [_] "\"hola\"")]
        (let [out (capture-stderr
                   #(do (produce/call spec :translate ["hi"])))]
          (is (str/includes? out "produce-prompt-built"))
          (is (str/includes? out "produce-llm-call-start"))
          (is (str/includes? out "produce-llm-call-done")))))))
```

Add `[drill.produce :as produce]` to the test ns require vector.

- [ ] **Step 2: Run tests to verify the new test fails**

Run: `bb test/runner.clj`

Expected: `produce-call-emits-trace-events` fails -- no trace output from produce yet.

- [ ] **Step 3: Add trace emit calls to produce.clj**

Add `[drill.trace :as trace]` to the require vector in `produce.clj`.

Replace the body of `call` with:

```clojure
(defn call [{:keys [id cases] :as spec} tag args]
  (let [case-spec (get cases tag)]
    (when-not case-spec
      (throw (ex-info (str "unknown-case: " id "/" tag)
                      {:type :drill/unknown-case :id id :tag tag})))
    (let [packed      (vec (cons tag args))
          _           (validate! (:input case-spec) packed :drill/input-invalid id tag)
          _           (trace/emit :debug {:stage :input-validated :id id :tag tag :input packed})
          prompt-text (build-prompt spec tag packed)]
      (trace/emit :info {:stage :produce-prompt-built :id id :tag tag :prompt-length (count prompt-text)})
      (trace/emit :debug {:stage :produce-prompt-built :id id :tag tag :prompt-length (count prompt-text) :prompt prompt-text})
      (trace/emit :info {:stage :produce-llm-call-start :id id :tag tag})
      (let [start    (System/currentTimeMillis)
            response (gen/*llm-call* prompt-text)
            elapsed  (- (System/currentTimeMillis) start)]
        (trace/emit :info {:stage :produce-llm-call-done :id id :tag tag :elapsed-ms elapsed})
        (trace/emit :debug {:stage :produce-llm-call-done :id id :tag tag :elapsed-ms elapsed :response response})
        (let [result (edn/read-string response)]
          (validate! (:output case-spec) result :drill/output-invalid id tag)
          (trace/emit :debug {:stage :output-validated :id id :tag tag :output result})
          (trace/emit :debug {:stage :produce-result :id id :tag tag :result result})
          result)))))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `bb test/runner.clj`

Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/drill/produce.clj test/drill/trace_test.clj
git commit -m "feat(trace): instrument produce path with trace events"
```

---

### Task 4: Instrument runtime validation

**Files:**
- Modify: `src/drill/runtime.clj`

- [ ] **Step 1: Write failing test for runtime validation trace events**

Add to `test/drill/trace_test.clj`:

```clojure
(deftest runtime-dispatch-emits-validation-events
  (testing "runtime/dispatch emits validation events at :debug level"
    (let [spec {:id :rt-test
                :kind :feature
                :lang :babashka
                :cases {:eval {:input [:tuple [:= :eval] :string]
                               :output :double}}}]
      (reg/clear!)
      (reg/register-spec! spec)
      (reg/register-impl! :rt-test (fn [[_tag & args]] (Double/parseDouble (first args))))
      (let [out (capture-stderr
                 #(binding [trace/*level* :debug]
                    (runtime/dispatch spec :eval ["3.14"])))]
        (is (str/includes? out "input-validated"))
        (is (str/includes? out "output-validated"))
        (reg/clear!)))))
```

Add `[drill.runtime :as runtime]` to the test ns require vector.

- [ ] **Step 2: Run tests to verify the new test fails**

Run: `bb test/runner.clj`

Expected: `runtime-dispatch-emits-validation-events` fails -- no trace output from runtime yet.

- [ ] **Step 3: Add trace emit calls to runtime.clj**

Add `[drill.trace :as trace]` to the require vector in `runtime.clj`.

Replace the body of `dispatch` with:

```clojure
(defn dispatch [{:keys [id cases]} tag args]
  (let [case-spec (get cases tag)]
    (when-not case-spec
      (throw (ex-info (str "unknown-case: " id "/" tag)
                      {:type :drill/unknown-case :id id :tag tag})))
    (let [packed  (vec (cons tag args))
          impl-fn (reg/lookup id)]
      (validate! (:input case-spec) packed :drill/input-invalid id tag)
      (trace/emit :debug {:stage :input-validated :id id :tag tag :input packed})
      (when-not impl-fn
        (throw (ex-info (str "impl-missing: " id)
                        {:type :drill/impl-missing :id id})))
      (let [result (impl-fn packed)]
        (validate! (:output case-spec) result :drill/output-invalid id tag)
        (trace/emit :debug {:stage :output-validated :id id :tag tag :output result})
        result))))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `bb test/runner.clj`

Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/drill/runtime.clj test/drill/trace_test.clj
git commit -m "feat(trace): instrument runtime dispatch with validation events"
```

---

### Task 5: CLI flags --verbose and --debug

**Files:**
- Modify: `src/drill/core.clj`

- [ ] **Step 1: Write failing test for CLI flag parsing**

Add to `test/drill/trace_test.clj`:

```clojure
(deftest cli-verbose-flag-sets-info-level
  (testing "--verbose sets *level* to :info during main"
    (reg/clear!)
    (reg/register-spec! {:id :cli-test :kind :feature :lang :babashka
                         :cases {:x {:input [:tuple [:= :x]] :output :any}}})
    (let [out (capture-stderr
               #(binding [gen/*generated-dir* "test/tmp"
                          gen/*llm-call* (fn [_]
                                          (str "```clojure\n"
                                               "(ns drill-generated.cli-test\n"
                                               "  (:require [drill.registry :as reg]))\n"
                                               "(defn -impl [x] nil)\n"
                                               "(reg/register-impl! :cli-test -impl)\n"
                                               "```"))]
                  (drill/main "--list" "--verbose")))]
      (is (= :off trace/*level*) "level restored after main returns"))
    (reg/clear!)))

(deftest cli-debug-flag-sets-debug-level
  (testing "--debug sets *level* to :debug during main"
    (reg/clear!)
    (let [captured (atom nil)]
      (reg/register-spec! {:id :cli-test2 :kind :feature :lang :babashka
                           :cases {:x {:input [:tuple [:= :x]] :output :any}}})
      (binding [gen/*generated-dir* "test/tmp"]
        (with-redefs [drill/list (fn []
                                   (reset! captured trace/*level*)
                                   [])]
          (drill/main "--list" "--debug")))
      (is (= :debug @captured))
      (reg/clear!))))
```

Add `[drill.core :as drill]` to the test ns require vector.

- [ ] **Step 2: Run tests to verify the new tests fail**

Run: `bb test/runner.clj`

Expected: Tests fail -- `--verbose` and `--debug` are not handled yet.

- [ ] **Step 3: Add flag parsing to core.clj**

Add `[drill.trace :as trace]` to the require vector in `core.clj`.

Replace the `main` function with:

```clojure
(defn main [& args]
  (let [argset (set args)
        level  (cond
                 (contains? argset "--debug")   :debug
                 (contains? argset "--verbose") :info
                 :else                          :off)]
    (binding [trace/*level* level]
      (cond
        (contains? argset "--list")
        (doseq [i (list)] (println i))

        (contains? argset "--check")
        (check)

        (contains? argset "--regen-all")
        (do (swap! reg/state assoc :generate? true)
            (try
              (doseq [r (regen-all)] (println r))
              (finally
                (swap! reg/state assoc :generate? false))))

        (or (contains? argset "--generate")
            (contains? argset "--regen-stale"))
        (do (swap! reg/state assoc :generate? true)
            (try
              (doseq [r (regen-stale)] (println r))
              (finally
                (swap! reg/state assoc :generate? false))))

        :else
        (when-let [f (:main-fn @reg/state)]
          (apply f args))))))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `bb test/runner.clj`

Expected: All tests pass.

- [ ] **Step 5: Run full test suite to verify no regressions**

Run: `bb test/runner.clj`

Expected: All 65+ tests pass, 0 failures, 0 errors.

- [ ] **Step 6: Commit**

```bash
git add src/drill/core.clj test/drill/trace_test.clj
git commit -m "feat(trace): add --verbose and --debug CLI flags"
```
