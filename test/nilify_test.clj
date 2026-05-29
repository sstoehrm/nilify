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
