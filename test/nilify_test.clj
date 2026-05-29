(ns nilify-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]))

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
                      (.mkdirs (io/file *test-dir* "nil" "features"))
                      (.mkdirs (io/file *test-dir* "nil" "systems"))
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
