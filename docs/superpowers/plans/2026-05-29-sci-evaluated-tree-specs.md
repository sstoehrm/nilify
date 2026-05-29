# sci-evaluated tree specs — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `nilify validate` evaluate a single tree-shaped spec file (with babashka's bundled sci) and validate the resulting tree against new Malli schemas, retiring the flat EDN model and resolving issue #6 parts 1 & 2.

**Architecture:** A tiny `nilify.core` namespace (`prompt`, `root`) is defined in-process inside the CLI; `validate` does `(load-string (slurp path))` so the spec's `(:require [nilify.core :as nilify])` resolves to it and the file's last form returns the tree. Validation runs three schema-level passes — structural (Malli), reference/visibility integrity (plain Clojure), and route-schema well-formedness (malli.generator). The flat `nil/features/` + `nil/systems/` model and its schemas/tests are removed.

**Tech Stack:** Babashka, Malli 0.20.1 (`malli.core`, `malli.error`, `malli.generator`), `clojure.test` (loaded via `load-file "nilify"`).

**Verified during planning (do not re-litigate):**
- Approach A works: `(ns nilify.core)` defined in the script makes `(:require [nilify.core :as nilify])` resolve in a `load-string`'d spec; the last form's value is returned; `prompt` joins with spaces; shared `def` vars resolve.
- Tree Malli schema (below) validates the `easy-calc` (layers-only) and `todo` (subsystems) shapes, and rejects: missing `:id`, mixed layer+subsystem body, bad node tag, empty layer.
- `[]` is **not** a valid malli schema → treated as the empty-payload sentinel (with `nil`); level-3 skips it. `spec-todo` and `[:vector spec-todo]` are generatable.

**Deviation from the design doc:** level-3 drops the "examples conform" sub-check — the tree model carries no `:examples` field (it was a flat-model concept). Level-3 is purely route-schema well-formedness. Revisit if routes later gain `:examples`.

---

## File structure (after this plan)

```
nilify                       Single CLI. Sections (top→bottom):
                               1. shebang + add-deps (malli)
                               2. (ns nilify.core) — prompt, root          [NEW]
                               3. (ns nilify.cli …)                        [edit requires: drop clojure.edn]
                               4. Tree schemas (FeatureProps…Root)         [REPLACES flat schemas]
                               5. check-structure                          [REPLACES validate-spec!/validate-system!]
                               6. tree-walk helpers + check-references     [REPLACES check-examples/check-connections]
                               7. check-schemas (level 3)                  [NEW]
                               8. load-tree + validate-all                 [REPLACES load-spec/load-system/load-dir/validate-all]
                               9. GitHub helpers                           [unchanged]
                              10. root-template + cmd-init                 [edit]
                              11. cmd-validate (path arg)                  [rewrite]
                              12. cmd-update/version/help                  [unchanged]
                              13. spec-reference + cmd-spec                [rewrite]
                              14. -main dispatch                           [edit: validate takes path]
test/nilify_test.clj         Rewritten suite (flat tests removed).
README.md                    Quick example / Spec tree / Validation / naming table updated.
skills/nilify/author/SKILL.md, validate/SKILL.md   Updated; others reviewed.
.claude/skills/working-on-nilify/SKILL.md          Spec-language section updated.
examples/easy-calc.clj, todo.clj                   Already canonical; verified to validate.
```

Run all tests with: `bb test/runner.clj`

---

## Task 1: `nilify.core` runtime + `load-tree`

**Files:**
- Modify: `nilify` (add `nilify.core` ns after the `add-deps` block, before `(ns nilify.cli …)`; add `load-tree` in `nilify.cli`)
- Test: `test/nilify_test.clj`

- [ ] **Step 1: Replace the flat-model test sections with a fresh scaffold + first tests**

Replace the entire current body of `test/nilify_test.clj` *below* the `use-fixtures` form (i.e. everything from `;; ---- Spec validation ----` to end of file) with this Task-1 block. Later tasks append more `deftest`s above the final integration section.

```clojure
;; ---- nilify.core runtime ----

(deftest prompt-joins-with-spaces
  (is (= "line one line two" (nilify.core/prompt "line one" "line two")))
  (is (= "solo" (nilify.core/prompt "solo"))))

(deftest root-returns-systems-vector
  (let [systems [[:system {:id :sys/a}]]]
    (is (= systems (nilify.core/root systems)))
    (is (vector? (nilify.core/root (seq systems))))))

;; ---- load-tree ----

(deftest load-tree-evaluates-and-returns-tree
  (testing "evaluating a spec file returns the tree; prompt + shared var resolve"
    (let [path (str *test-dir* "/spec.clj")]
      (spit path
            (str "(ns demo (:require [nilify.core :as nilify]))\n"
                 "(def s [:map [:result :double]])\n"
                 "(nilify/root [[:system {:id :sys/demo "
                 ":desc (nilify/prompt \"a\" \"b\")} "
                 "[:layer [:feature {:id :feat/x :internals {:out s}}]]]])\n"))
      (let [tree (cli/load-tree path)]
        (is (vector? tree))
        (is (= :sys/demo (get-in (first tree) [1 :id])))
        (is (= "a b" (get-in (first tree) [1 :desc])))))))
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `bb test/runner.clj`
Expected: FAIL — `nilify.core` / `cli/load-tree` not defined (or test file won't load).

- [ ] **Step 3: Add the `nilify.core` namespace**

In `nilify`, immediately after the `(deps/add-deps …)` line and before `(ns nilify.cli …)`, insert:

```clojure
(ns nilify.core
  (:require [clojure.string :as str]))

(defn prompt
  "Join description fragments into one string."
  [& parts]
  (str/join " " parts))

(defn root
  "Spec entry point: return the vector of system nodes as the tree."
  [systems]
  (vec systems))
```

- [ ] **Step 4: Add `load-tree`**

In the "Core helpers" area (next to the existing `load-spec`), add `load-tree`. Do **not** remove anything yet — `load-spec`/`load-system`/`clojure.edn` stay until Task 5 (the file must keep loading at every step).

```clojure
(defn load-tree
  "Evaluate a spec file with sci; its last form (nilify/root …) returns the tree."
  [path]
  (load-string (slurp path)))
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `bb test/runner.clj`
Expected: the three Task-1 tests PASS. (Other removed tests no longer exist; remaining flat-model functions are untouched for now but unreferenced by tests.)

- [ ] **Step 6: Commit**

```bash
git add nilify test/nilify_test.clj
git commit -m "feat: add nilify.core runtime and sci-based load-tree"
```

---

## Task 2: Tree schemas + `check-structure`

**Files:**
- Modify: `nilify` (replace the flat schema block + `validate-spec!`/`validate-system!`)
- Test: `test/nilify_test.clj`

- [ ] **Step 1: Add structural-validation tests**

Append to `test/nilify_test.clj` (above any integration section):

```clojure
;; ---- Structural validation ----

(def ^:private calc-tree
  [[:system {:id :sys/calc :tech "tui" :desc "calc"}
    [:layer [:feature {:id :feat/ui :desc "ui" :internals {:screens {}}}]]
    [:layer [:feature {:id :feat/translate :desc "t"}]
            [:feature {:id :feat/compute :desc "c"}]]]])

(def ^:private todo-tree
  [[:system {:id :sys/frontend :tech "react"
             :connects-to #{[:sys/backend :iface/backend-api]}}
    [:layer [:feature {:id :feat/search-ui :desc "s"}]]]
   [:system {:id :sys/backend :tech "bb" :desc "be" :provides [:iface/backend-api]}
    [:subsystem {:id :sub/main
                 :provides {["HTTP GET" ["/"]]
                            {:interface :iface/backend-api :input [] :output [:vector :map]}}}
     [:layer [:feature {:id :feat/api :desc "api"}]
             [:feature {:id :feat/database :tech "sqlite" :desc "db"}]]
     [:layer [:feature {:id :feat/domain-model :internals {"dm" :map}}]]]]])

(deftest valid-trees-pass-structure
  (is (nil? (cli/check-structure calc-tree)))
  (is (nil? (cli/check-structure todo-tree))))

(deftest structure-rejects-missing-system-id
  (is (some? (cli/check-structure [[:system {:tech "x"} [:layer [:feature {:id :feat/a}]]]]))))

(deftest structure-rejects-mixed-body
  (is (some? (cli/check-structure
              [[:system {:id :sys/x}
                [:layer [:feature {:id :feat/a}]]
                [:subsystem {:id :sub/b} [:layer [:feature {:id :feat/c}]]]]]))))

(deftest structure-rejects-bad-tag-and-empty-layer
  (is (some? (cli/check-structure [[:system {:id :sys/x} [:layer [:nope {:id :feat/a}]]]])))
  (is (some? (cli/check-structure [[:system {:id :sys/x} [:layer]]]))))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `bb test/runner.clj`
Expected: FAIL — `cli/check-structure` undefined.

- [ ] **Step 3: Add the tree schema + `check-structure` (additive)**

In `nilify`, **add** the following as a new section immediately *after* the existing flat `validate-system!`. Do **not** delete the flat schemas yet — the entire flat cluster is removed atomically in Task 5, so the file keeps loading at every step. (New names don't collide with flat names.)

```clojure
;; ---- Tree spec schemas ----

(def FeatureProps
  [:map
   [:id :keyword]
   [:tech      {:optional true} :string]
   [:desc      {:optional true} :string]
   [:internals {:optional true} [:map-of :any :any]]])

(def Feature [:catn [:tag [:enum :feature]] [:props FeatureProps]])
(def Layer   [:catn [:tag [:enum :layer]] [:features [:+ [:schema Feature]]]])

(def ProvidesEntry
  [:map
   [:interface :keyword]
   [:input  :any]    ; a Malli schema, or [] / nil for empty payload
   [:output :any]])

(def SubProps
  [:map
   [:id :keyword]
   [:uses     {:optional true} [:vector :keyword]]
   [:provides {:optional true} [:map-of :any ProvidesEntry]]])

(def Subsystem
  [:catn [:tag [:enum :subsystem]] [:props SubProps]
   [:layers [:+ [:schema Layer]]]])

(def SysProps
  [:map
   [:id :keyword]
   [:tech        {:optional true} :string]
   [:desc        {:optional true} :string]
   [:provides    {:optional true} [:vector :keyword]]
   [:connects-to {:optional true} [:set [:tuple :keyword :keyword]]]])

(def System
  [:catn [:tag [:enum :system]] [:props SysProps]
   ;; a system's children are all layers OR all subsystems, never mixed
   [:body [:alt [:+ [:schema Layer]] [:+ [:schema Subsystem]]]]])

(def Root [:+ [:schema System]])

(defn check-structure
  "nil if the tree is structurally valid, else humanized Malli errors."
  [tree]
  (when-not (m/validate Root tree)
    (me/humanize (m/explain Root tree))))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `bb test/runner.clj`
Expected: the four structural tests PASS.

- [ ] **Step 5: Commit**

```bash
git add nilify test/nilify_test.clj
git commit -m "feat: tree Malli schema and structural validation"
```

---

## Task 3: Tree-walk helpers + `check-references`

**Files:**
- Modify: `nilify` (replace `check-one-example`/`check-examples`/`check-one-connection`/`check-connections` with helpers + `check-references`)
- Test: `test/nilify_test.clj`

- [ ] **Step 1: Add reference-integrity tests**

Append to `test/nilify_test.clj`:

```clojure
;; ---- Reference & visibility integrity ----

(deftest references-pass-on-valid-trees
  (is (empty? (cli/check-references calc-tree)))
  (is (empty? (cli/check-references todo-tree))))

(deftest connects-to-unknown-system-fails
  (let [t [[:system {:id :sys/a :connects-to #{[:sys/ghost :iface/x]}}
            [:layer [:feature {:id :feat/a}]]]]]
    (is (some #(clojure.string/includes? % "unknown system") (cli/check-references t)))))

(deftest connects-to-undeclared-interface-fails
  (let [t [[:system {:id :sys/a :connects-to #{[:sys/b :iface/missing]}}
            [:layer [:feature {:id :feat/a}]]]
           [:system {:id :sys/b :provides [:iface/real]}
            [:subsystem {:id :sub/m
                         :provides {["GET" []] {:interface :iface/real :input [] :output []}}}
             [:layer [:feature {:id :feat/b}]]]]]]
    (is (some #(clojure.string/includes? % "does not provide") (cli/check-references t)))))

(deftest provides-without-definition-fails
  (let [t [[:system {:id :sys/b :provides [:iface/real]}
            [:subsystem {:id :sub/m} [:layer [:feature {:id :feat/b}]]]]]]
    (is (some #(clojure.string/includes? % "no subsystem defines") (cli/check-references t)))))

(deftest route-without-advertisement-fails
  (let [t [[:system {:id :sys/b}
            [:subsystem {:id :sub/m
                         :provides {["GET" []] {:interface :iface/secret :input [] :output []}}}
             [:layer [:feature {:id :feat/b}]]]]]]
    (is (some #(clojure.string/includes? % "does not advertise") (cli/check-references t)))))

(deftest uses-unknown-subsystem-fails
  (let [t [[:system {:id :sys/b}
            [:subsystem {:id :sub/a :uses [:sub/ghost]} [:layer [:feature {:id :feat/a}]]]
            [:subsystem {:id :sub/b} [:layer [:feature {:id :feat/b}]]]]]]
    (is (some #(clojure.string/includes? % "uses unknown subsystem") (cli/check-references t)))))

(deftest duplicate-ids-fail
  (let [t [[:system {:id :sys/a} [:layer [:feature {:id :feat/dup}]]]
           [:system {:id :sys/b} [:layer [:feature {:id :feat/dup}]]]]]
    (is (some #(clojure.string/includes? % "duplicate id") (cli/check-references t)))))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `bb test/runner.clj`
Expected: FAIL — `cli/check-references` undefined.

- [ ] **Step 3: Add tree-walk helpers + `check-references` (additive)**

In `nilify`, **add** the following as a new section *after* the existing flat `check-connections`. Do **not** delete the flat validation functions yet — they are removed atomically in Task 5. (New names don't collide with flat names.)

```clojure
;; ---- Tree-walk helpers ----

(defn- props [node] (second node))
(defn- node-tag [node] (first node))
(defn- system-subsystems [sys] (filter #(= :subsystem (node-tag %)) (drop 2 sys)))
(defn- system-layers [sys] (filter #(= :layer (node-tag %)) (drop 2 sys)))
(defn- subsystem-layers [sub] (drop 2 sub))
(defn- layer-features [layer] (rest layer))

(defn- all-layers [sys]
  (concat (system-layers sys)
          (mapcat subsystem-layers (system-subsystems sys))))

(defn- all-features [tree]
  (mapcat (fn [sys] (mapcat layer-features (all-layers sys))) tree))

(defn- all-ids [tree]
  (concat (map #(:id (props %)) tree)
          (map #(:id (props %)) (mapcat system-subsystems tree))
          (map #(:id (props %)) (all-features tree))))

;; ---- Reference & visibility integrity (level 2) ----

(defn check-references
  "Return a vector of human-readable reference/integrity problems (empty if clean)."
  [tree]
  (let [sys-by-id (into {} (map (juxt #(:id (props %)) identity) tree))
        problems  (atom [])
        add! (fn [m] (swap! problems conj m))]
    ;; connects-to integrity
    (doseq [sys tree
            [tsys tiface] (:connects-to (props sys))]
      (let [target (sys-by-id tsys)]
        (cond
          (nil? target)
          (add! (str (:id (props sys)) " connects-to unknown system " tsys))
          (not (some #{tiface} (:provides (props target))))
          (add! (str (:id (props sys)) " connects-to " tsys " " tiface
                     ", but that system does not provide it")))))
    ;; provides declaration <-> definition
    (doseq [sys tree]
      (let [declared (set (:provides (props sys)))
            defined  (set (mapcat (fn [sub] (map :interface (vals (:provides (props sub)))))
                                  (system-subsystems sys)))]
        (doseq [iface declared :when (not (defined iface))]
          (add! (str (:id (props sys)) " provides " iface
                     ", but no subsystem defines routes for it")))
        (doseq [iface defined :when (not (declared iface))]
          (add! (str (:id (props sys)) " has a route for " iface
                     ", but does not advertise it in :provides")))))
    ;; uses integrity
    (doseq [sys tree]
      (let [sub-ids (set (map #(:id (props %)) (system-subsystems sys)))]
        (doseq [sub (system-subsystems sys)
                used (:uses (props sub)) :when (not (sub-ids used))]
          (add! (str (:id (props sub)) " uses unknown subsystem " used)))))
    ;; unique ids
    (doseq [[id n] (frequencies (all-ids tree)) :when (> n 1)]
      (add! (str "duplicate id " id)))
    @problems))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `bb test/runner.clj`
Expected: all reference tests PASS.

- [ ] **Step 5: Commit**

```bash
git add nilify test/nilify_test.clj
git commit -m "feat: tree-walk helpers and reference-integrity checks"
```

---

## Task 4: `check-schemas` (level 3)

**Files:**
- Modify: `nilify` (add `check-schemas` after `check-references`)
- Test: `test/nilify_test.clj`

- [ ] **Step 1: Add schema-well-formedness tests**

Append to `test/nilify_test.clj`:

```clojure
;; ---- Route schema well-formedness (level 3) ----

(deftest schemas-pass-on-valid-routes
  (is (empty? (cli/check-schemas todo-tree))))

(deftest empty-payload-is-allowed
  (let [t [[:system {:id :sys/b :provides [:iface/x]}
            [:subsystem {:id :sub/m
                         :provides {["POST" []] {:interface :iface/x :input [] :output nil}}}
             [:layer [:feature {:id :feat/b}]]]]]]
    (is (empty? (cli/check-schemas t)))))

(deftest bogus-route-schema-fails
  (let [t [[:system {:id :sys/b :provides [:iface/x]}
            [:subsystem {:id :sub/m
                         :provides {["GET" []] {:interface :iface/x :input [:nope] :output []}}}
             [:layer [:feature {:id :feat/b}]]]]]]
    (is (seq (cli/check-schemas t)))))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `bb test/runner.clj`
Expected: FAIL — `cli/check-schemas` undefined.

- [ ] **Step 3: Implement `check-schemas`**

In `nilify`, immediately after `check-references`, add:

```clojure
;; ---- Route schema well-formedness (level 3) ----

(defn- empty-payload? [s] (or (nil? s) (= s [])))

(defn check-schemas
  "Return problems for provider route :input/:output that are not valid,
   generatable Malli schemas. [] and nil mean empty payload and are skipped."
  [tree]
  (let [problems (atom [])]
    (doseq [sys tree
            sub (system-subsystems sys)
            [route entry] (:provides (props sub))
            k [:input :output]
            :let [s (get entry k)]
            :when (not (empty-payload? s))]
      (try
        (mg/generate s)
        (catch Exception e
          (swap! problems conj
                 (str (:id (props sub)) " route " (pr-str route) " " k
                      " is not a valid schema: " (ex-message e))))))
    @problems))
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `bb test/runner.clj`
Expected: schema tests PASS.

- [ ] **Step 5: Commit**

```bash
git add nilify test/nilify_test.clj
git commit -m "feat: route schema well-formedness checks (level 3)"
```

---

## Task 5: `validate-all` + `cmd-validate` (path arg) + integration

**Files:**
- Modify: `nilify` (replace `load-spec`/`load-system`/`load-dir`/`validate-all` and `cmd-validate`; update `-main`)
- Test: `test/nilify_test.clj`

- [ ] **Step 1: Add `validate-all` unit test + subprocess integration tests**

Append the `validate-all` test near the other unit tests:

```clojure
;; ---- validate-all orchestration ----

(deftest validate-all-aggregates-levels
  (let [ok (cli/validate-all todo-tree)]
    (is (nil? (:structure ok)))
    (is (empty? (:references ok)))
    (is (empty? (:schemas ok))))
  (let [bad (cli/validate-all [[:system {:id :sys/a
                                         :connects-to #{[:sys/ghost :iface/x]}}
                                [:layer [:feature {:id :feat/a}]]]])]
    (is (seq (:references bad)))))
```

Then replace the existing `;; ---- CLI integration ----` section (the `validate-from-directories`, `cli-path`, and `validate-cli-command` tests) with:

```clojure
;; ---- CLI integration ----

(def cli-path (.getAbsolutePath (io/file "nilify")))

(defn- run-validate [dir & args]
  (apply babashka.process/shell
         {:out :string :err :string :continue true :dir dir}
         "bb" cli-path "validate" args))

(deftest validate-cli-passes-on-valid-root
  (spit (str *test-dir* "/nil/root.clj")
        (str "(ns p (:require [nilify.core :as nilify]))\n"
             "(nilify/root [[:system {:id :sys/a "
             ":desc (nilify/prompt \"x\")} "
             "[:layer [:feature {:id :feat/a}]]]])\n"))
    ;; root.clj lives at <dir>/nil/root.clj; default path is nil/root.clj
  (let [{:keys [exit out]} (run-validate *test-dir*)]
    (is (zero? exit))
    (is (clojure.string/includes? out "Structure: ok"))))

(deftest validate-cli-fails-on-bad-reference
  (spit (str *test-dir* "/nil/root.clj")
        (str "(ns p (:require [nilify.core :as nilify]))\n"
             "(nilify/root [[:system {:id :sys/a "
             ":connects-to #{[:sys/ghost :iface/x]}} "
             "[:layer [:feature {:id :feat/a}]]]])\n"))
  (let [{:keys [exit out]} (run-validate *test-dir*)]
    (is (= 1 exit))
    (is (clojure.string/includes? out "unknown system"))))
```

Add `babashka.process` to the test ns require:

```clojure
(ns nilify-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [clojure.string]
            [babashka.process]))
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `bb test/runner.clj`
Expected: FAIL — `cli/validate-all` has the wrong arity / `cmd-validate` still scans dirs.

- [ ] **Step 3: Remove the flat cluster atomically + add new `validate-all`**

Now that all tree functions exist and nothing new references the flat code, remove the **entire** flat cluster in one consistent edit (so the file never has dangling references). Delete:
- the flat schema block: `Case`, `FeatureSpec`, `Component`, `Endpoint`, `Connection`, `SystemSpec`, `validate-spec!`, `validate-system!`
- the flat validation block: `check-one-example`, `check-examples`, `sample-count`, `check-one-connection`, `check-connections`
- the flat loaders: `load-spec`, `load-system`, `load-dir`, and the **old** `validate-all`

Keep `load-tree`. Also remove `[clojure.edn :as edn]` from the `nilify.cli` requires (only the flat loaders used it). Then add the new `validate-all`:

```clojure
(defn validate-all
  "Run all three validation passes on an already-loaded tree."
  [tree]
  (let [structure (check-structure tree)]
    (if structure
      {:structure structure :references [] :schemas []}
      {:structure nil
       :references (check-references tree)
       :schemas    (check-schemas tree)})))
```

After this edit, run `bb -e '(load-file "nilify")'` (or the tests) to confirm the file still loads with no unresolved symbols.

- [ ] **Step 4: Rewrite `cmd-validate` and the dispatch**

Replace the whole `cmd-validate` def with:

```clojure
(defn cmd-validate [& [path]]
  (let [spec-path (or path "nil/root.clj")]
    (when-not (.exists (io/file spec-path))
      (println (str "No spec file at " spec-path
                    ". Run 'nilify init' first, or pass a path."))
      (System/exit 1))
    (let [tree (try
                 (load-tree spec-path)
                 (catch Exception e
                   (println (str "Could not evaluate " spec-path ": " (ex-message e)))
                   (System/exit 1)))
          {:keys [structure references schemas]} (validate-all tree)]
      (println (str "Validation results (" spec-path "):"))
      (if structure
        (do (println "  Structure: FAIL")
            (println (str "    " (pr-str structure)))
            (System/exit 1))
        (do
          (println "  Structure: ok")
          (println (str "  References: " (count references) " problem(s)"))
          (println (str "  Schemas: " (count schemas) " problem(s)"))
          (doseq [p (concat references schemas)] (println (str "  - " p)))
          (when (or (seq references) (seq schemas)) (System/exit 1)))))))
```

In `-main`, change the validate branch from `"validate" (cmd-validate)` to:

```clojure
      "validate" (apply cmd-validate (rest args))
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `bb test/runner.clj`
Expected: `validate-all` + both integration tests PASS.

- [ ] **Step 6: Commit**

```bash
git add nilify test/nilify_test.clj
git commit -m "feat: tree validate-all and path-aware cmd-validate"
```

---

## Task 6: `cmd-init` writes `nil/root.clj`

**Files:**
- Modify: `nilify` (add `root-template`; edit `cmd-init`)
- Test: `test/nilify_test.clj`

- [ ] **Step 1: Add an init test**

Append to `test/nilify_test.clj` (before the integration section is fine):

```clojure
;; ---- init starter ----

(deftest init-starter-template-validates
  (testing "the embedded starter template is a structurally valid tree"
    (let [path (str *test-dir* "/starter.clj")]
      (spit path cli/root-template)
      (is (nil? (cli/check-structure (cli/load-tree path)))))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bb test/runner.clj`
Expected: FAIL — `cli/root-template` undefined.

- [ ] **Step 3: Add `root-template` and rewrite `cmd-init`'s directory setup**

In `nilify`, just before `cmd-init`, add:

```clojure
(def root-template
  "(ns my-project\n  (:require [nilify.core :as nilify]))\n\n(nilify/root\n [[:system\n   {:id :sys/example\n    :tech \"your stack here\"\n    :desc (nilify/prompt \"What this system does.\")}\n   [:layer\n    [:feature\n     {:id :feat/example\n      :desc (nilify/prompt \"What this feature does.\")}]]]])\n")
```

In `cmd-init`, replace the `nil` directory block:

```clojure
  (let [nil-dir (io/file "nil")]
    (.mkdirs (io/file nil-dir "features"))
    (.mkdirs (io/file nil-dir "systems"))
    (println "  Created nil/features/ and nil/systems/"))
```

with:

```clojure
  (let [nil-dir (io/file "nil")
        root-file (io/file nil-dir "root.clj")]
    (.mkdirs nil-dir)
    (if (.exists root-file)
      (println "  nil/root.clj already exists, leaving it untouched")
      (do (spit root-file root-template)
          (println "  Created nil/root.clj"))))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `bb test/runner.clj`
Expected: init test PASS.

- [ ] **Step 5: Commit**

```bash
git add nilify test/nilify_test.clj
git commit -m "feat: init writes a nil/root.clj starter template"
```

---

## Task 7: Rewrite `spec-reference` + bump version

**Files:**
- Modify: `nilify` (replace `spec-reference` string; bump `version`)
- Test: `test/nilify_test.clj`

- [ ] **Step 1: Add a light spec-reference test**

Append to `test/nilify_test.clj`:

```clojure
;; ---- spec reference ----

(deftest spec-reference-describes-the-tree
  (is (clojure.string/includes? cli/spec-reference ":subsystem"))
  (is (clojure.string/includes? cli/spec-reference "nilify/root"))
  (is (not (clojure.string/includes? cli/spec-reference ":components")))
  (is (not (clojure.string/includes? cli/spec-reference ":cases"))))
```

- [ ] **Step 2: Run test to verify it fails**

Run: `bb test/runner.clj`
Expected: FAIL — old `spec-reference` still mentions `:cases`/`:components`.

- [ ] **Step 3: Replace `spec-reference` and bump `version`**

Change `(def version "0.1.0")` to `(def version "0.2.0")`.

Replace the entire `(def spec-reference "…")` string with:

```clojure
(def spec-reference
"; nilify spec reference -- every field documented in one place.
;
; A project's spec is ONE Clojure file (default nil/root.clj) that, when
; evaluated, returns a tree via (nilify/root [...]). `nilify validate`
; evaluates it with sci -- so (nilify/prompt ...) and shared `def` schema
; vars work. The last form must be the (nilify/root ...) call.

(ns my-project
  (:require [nilify.core :as nilify]))

;; Shared Malli schemas as ordinary vars, referenced anywhere in the tree:
(def spec-result [:map [:result :double]])

(nilify/root
 [[:system                                  ; one or more systems
   {:id          :sys/backend               ; required, :sys/<name>
    :tech        \"http-server babashka\"     ; optional, free-form
    :desc        (nilify/prompt \"…\" \"…\")    ; optional, prompt joins with spaces
    :provides    [:iface/backend-api]        ; optional, interface NAMES advertised
    :connects-to #{[:sys/other :iface/x]}}   ; optional, interfaces consumed

   ;; A system's children are EITHER layers (simple) OR subsystems (composed),
   ;; never mixed. Layers/subsystems are ordered TOP-FIRST: the first layer is
   ;; the topmost/usable one; later layers are lower and more foundational;
   ;; dependencies flow downward.

   [:subsystem
    {:id       :sub/main                     ; required, :sub/<name>
     :uses     [:sub/other]                  ; optional, sibling subsystems used
     :provides                               ; optional, TYPED interface definitions
     {[\"HTTP GET\" [\"/\"]]                     ; route key: free-form (method + path)
      {:interface :iface/backend-api         ; must be advertised in system :provides
       :input  []                            ; Malli schema, or [] / nil for empty
       :output [:vector spec-result]}}}      ; Malli schema

    [:layer                                  ; topmost layer (usable)
     [:feature
      {:id        :feat/api                  ; required, :feat/<name>
       :tech      \"compojure\"                ; optional per-feature tech
       :desc      (nilify/prompt \"…\")}]]
    [:layer                                  ; lower layer (private)
     [:feature
      {:id        :feat/domain-model
       :internals {:domain spec-result}}]]]]]) ; optional, free-form domain knowledge

;; ---------------------------------------------------------------------------
;; Naming conventions (by convention)
;; ---------------------------------------------------------------------------
;; :sys/<name>   system ids        :sub/<name>    subsystem ids
;; :feat/<name>  feature ids       :iface/<name>  interface names
;; :<feature>/<field>  schema fields owned by a feature

;; ---------------------------------------------------------------------------
;; Validation (nilify validate) -- three schema-level passes, no project code run
;; ---------------------------------------------------------------------------
;; 1. Structure   -- the tree conforms to the node schemas above
;; 2. References  -- connects-to resolves to a provided interface; every advertised
;;                   interface is defined by a subsystem route (and vice versa);
;;                   :uses resolves to a sibling subsystem; ids are unique
;; 3. Schemas     -- each route :input/:output is a valid, generatable Malli schema
")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `bb test/runner.clj`
Expected: spec-reference test PASS. Also check `bb nilify version` prints `nilify 0.2.0`.

- [ ] **Step 5: Commit**

```bash
git add nilify test/nilify_test.clj
git commit -m "feat: tree spec reference and version bump to 0.2.0"
```

---

## Task 8: Verify examples validate end-to-end

**Files:** none modified (verification + a regression test)
- Test: `test/nilify_test.clj`

- [ ] **Step 1: Add a regression test that the shipped examples validate**

Append to `test/nilify_test.clj`:

```clojure
;; ---- shipped examples ----

(deftest shipped-examples-validate
  (doseq [ex ["examples/easy-calc.clj" "examples/todo.clj"]]
    (testing ex
      (let [{:keys [structure references schemas]}
            (cli/validate-all (cli/load-tree ex))]
        (is (nil? structure) (str ex " structure"))
        (is (empty? references) (str ex " references: " references))
        (is (empty? schemas) (str ex " schemas: " schemas))))))
```

- [ ] **Step 2: Run the test**

Run: `bb test/runner.clj`
Expected: PASS. If `todo.clj`/`easy-calc.clj` fail, fix the example (these are the user's in-progress edits — e.g. confirm `:sub/` prefix, balanced forms, `:interface` values match the system `:provides`). Do not loosen the checks to make examples pass.

- [ ] **Step 3: Manual smoke check**

Run: `bb nilify validate examples/todo.clj`
Expected: prints `Structure: ok`, `References: 0 problem(s)`, `Schemas: 0 problem(s)`, exit 0.

- [ ] **Step 4: Commit**

```bash
git add test/nilify_test.clj
git commit -m "test: shipped examples validate against the tree model"
```

---

## Task 9: Update README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Update the Quick example**

Ensure the README Quick example matches `examples/todo.clj` shape: ends with a bare `(nilify/root [...])` (no `def root`), and includes a `:subsystem` with a typed `:provides` plus a system-level `:provides [:iface/...]`. Copy the structure from `examples/todo.clj`.

- [ ] **Step 2: Update the "Spec tree" section**

Replace the tree diagram and bullets with:

```
root
  system (:tech, :provides, :connects-to)
    subsystem (:uses, :provides)        ; optional; groups layers
      layer (ordered top-first)
        feature (:desc, :internals)
```

- **root** — one per project; the file's last form, `(nilify/root [...])`
- **system** — deployable unit. Advertises interfaces by name in `:provides`; consumes them via `:connects-to`. Children are all layers OR all subsystems
- **subsystem** — groups layers and owns the *typed* interface definitions (`:provides`); declares cross-subsystem use via `:uses`
- **layer** — ordered top-first: the first layer is topmost/usable, later layers are lower/foundational; only the top layer is usable by consumers
- **feature** — leaf node; unit of generation

- [ ] **Step 3: Update the Validation section**

Replace the three bullets with the new passes:

1. **Structure** — the tree conforms to the node schemas
2. **References** — `:connects-to` resolves to a provided interface; advertised interfaces are defined by subsystem routes (and vice versa); `:uses` resolves to a sibling subsystem; ids are unique
3. **Schemas** — each route `:input`/`:output` is a valid, generatable Malli schema (`[]`/`nil` = empty payload)

- [ ] **Step 4: Update the naming table**

Add a `:sub/` row: `| \`:sub/\` | Subsystem id | \`:sub/main\` |`.

- [ ] **Step 5: Verify markdown + commit**

Run: `bb nilify validate examples/easy-calc.clj` (sanity that the documented shape validates).
```bash
git add README.md
git commit -m "docs: README reflects the tree model and subsystems"
```

---

## Task 10: Update skills + working-on-nilify

**Files:**
- Modify: `skills/nilify/author/SKILL.md`, `skills/nilify/validate/SKILL.md`, `.claude/skills/working-on-nilify/SKILL.md`
- Review: `skills/nilify/{nilify,extract,onboard,diff,generate}/SKILL.md`

- [ ] **Step 1: `nilify-author`** — remove the `nilc/prompt`-for-flat guidance and the "one file per project using `nilc/root`, or separate files in `nil/features/`" wording. Describe the tree: systems → (layers | subsystems) → layers → features; system `:provides` names vs subsystem typed `:provides`; `:connects-to`; `:uses`; top-first layering; `:desc` via `nilify/prompt`. Point authoring at a single `nil/root.clj`.

- [ ] **Step 2: `nilify-validate`** — the three "What the three levels check" sections must match the new passes (Structure / References / Schemas as in Task 9 Step 3). Update the sample "Reporting" block to the new output (`Structure: ok` / `References: N problem(s)` / `Schemas: N problem(s)`). The invocation (`nilify validate`) is already correct from the earlier #6 part-3 fix.

- [ ] **Step 3: `working-on-nilify`** — in the spec-language section: node types now include `subsystem`; ids include `:sub/`; layout is a single `nil/root.clj` (not `nil/features/`+`nil/systems/`); validation passes are Structure/References/Schemas. In "Open work / known gaps", remove the "runtime library — `nilify.core` doesn't exist" item (now implemented) and note that examples now validate.

- [ ] **Step 4: Review the remaining skills** — grep and fix any flat-model references:

Run: `grep -rln ":cases\|:components\|:connections\|nil/features\|nil/systems\|nilc/\|nil\.core" skills/`
For each hit in `nilify`, `extract`, `onboard`, `diff`, `generate`: update prose to the tree model (or remove if obsolete). Keep edits minimal and prose-only.

- [ ] **Step 5: Commit**

```bash
git add skills/ .claude/skills/working-on-nilify/SKILL.md
git commit -m "docs: skills reflect the tree model, subsystems, and single root file"
```

---

## Task 11: Final verification

- [ ] **Step 1: Full test run**

Run: `bb test/runner.clj`
Expected: all tests pass; report the count.

- [ ] **Step 2: CLI smoke tests**

Run each and confirm:
- `bb nilify version` → `nilify 0.2.0`
- `bb nilify spec` → prints the tree reference (mentions `:subsystem`, no `:cases`)
- `bb nilify validate examples/todo.clj` → all passes ok, exit 0
- In a temp dir: `bb /abs/path/nilify init` then `bb /abs/path/nilify validate` → starter `nil/root.clj` validates

- [ ] **Step 3: Confirm no stale flat-model references remain**

Run: `grep -rn ":cases\|:components\|nil/features\|nil/systems\|nil\.core\|nilc/" nilify README.md skills/ .claude/`
Expected: no results (or only intentional historical mentions). Fix stragglers.

- [ ] **Step 4: Final commit if needed, then report**

The branch `tree-spec-sci-eval` now resolves issue #6 parts 1 & 2 (and part 3 was fixed earlier). Summarize for the user and offer to open a PR / close #6. Do not push or open the PR without the user's go-ahead.

---

## Self-review (completed during planning)

- **Spec coverage:** loader+runtime → T1; tree schema + structure → T2; references/visibility → T3; route schemas → T4; validate-all + cmd-validate + file layout (`nil/root.clj`, path arg) → T5/T6; spec reference + `:sub/` + top-first + version → T6/T7/T9; replace flat model (schemas/loaders/tests removed) → T2/T3/T5; examples → T8; README/skills/working-on-nilify → T9/T10. The dropped pairwise type-compat and dropped examples-conformance are noted as deliberate deviations.
- **Placeholder scan:** every code step contains complete, spike-verified code; doc tasks specify exact content and a verification command.
- **Type consistency:** function names used across tasks — `check-structure`, `check-references`, `check-schemas`, `validate-all` (1-arg, takes a tree), `load-tree`, `root-template`, `nilify.core/prompt`, `nilify.core/root` — are consistent everywhere they appear.
