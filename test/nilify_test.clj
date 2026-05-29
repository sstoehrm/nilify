(ns nilify-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [clojure.string]
            [babashka.process]))

(load-file "nilify")

(alias 'cli 'nilify.cli)

(def ^:dynamic *test-dir* "test/tmp/nilify")

(defn- delete-tree [dir]
  (let [d (io/file dir)]
    (when (.exists d)
      (doseq [f (reverse (file-seq d))]
        (.delete f)))))

(use-fixtures :each (fn [f]
                      (delete-tree *test-dir*)
                      (.mkdirs (io/file *test-dir* "nil"))
                      (f)))

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
  (testing "evaluating a spec file returns the tree (nilify.core must already be loaded in the runtime); prompt + shared var resolve"
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
    (is (seq (cli/check-schemas t)))
    (is (some #(clojure.string/includes? % "sub/m") (cli/check-schemas t)))))

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
