(ns nilify.spec-test
  (:require [clojure.test :refer [deftest is testing]]
            [nilify.spec :as spec]))

(deftest valid-feature-spec-passes
  (testing "a well-formed feature spec passes validation"
    (let [s {:id :compute
             :desc "evaluate expressions"
             :cases {:eval {:input [:map [:expr :string]]
                            :output [:map [:result :double]]
                            :examples [{:in {:expr "(+ 1 2)"} :out {:result 3.0}}]}}}]
      (is (= s (spec/validate-spec! s))))))

(deftest spec-requires-id
  (testing "spec without :id throws"
    (is (thrown-with-msg? Exception #"invalid-spec"
          (spec/validate-spec! {:cases {:x {:input :any :output :any}}})))))

(deftest feature-without-cases-passes
  (testing "feature without :cases is valid (tree-style features use :desc + :internals)"
    (let [s {:id :ui :desc "a user interface"}]
      (is (= s (spec/validate-spec! s))))))

(deftest spec-with-deps-passes
  (testing "spec with :deps validates"
    (let [s {:id :calc :deps [:translate :compute]
             :cases {:x {:input [:map [:q :string]] :output [:map [:r :double]]}}}]
      (is (= s (spec/validate-spec! s))))))

(deftest spec-with-tech-passes
  (testing "spec with :tech validates"
    (let [s {:id :db :tech "sqlite" :desc "database layer"}]
      (is (= s (spec/validate-spec! s))))))

(deftest valid-system-spec-passes
  (testing "a well-formed system spec passes validation"
    (let [s {:id :calculator
             :desc "NL calculator"
             :components {:translate {:feature :translate :lang :python}
                          :compute   {:feature :compute   :lang :babashka}}
             :connections [[[:translate :translate :output] [:compute :eval :input]]]}]
      (is (= s (spec/validate-system! s))))))

(deftest system-requires-id
  (testing "system without :id throws"
    (is (thrown-with-msg? Exception #"invalid-system"
          (spec/validate-system! {:components {:x {:feature :x :lang :python}}})))))

(deftest system-requires-components
  (testing "system without :components throws"
    (is (thrown-with-msg? Exception #"invalid-system"
          (spec/validate-system! {:id :broken})))))

(deftest system-component-requires-feature-and-lang
  (testing "component missing :feature or :lang throws"
    (is (thrown-with-msg? Exception #"invalid-system"
          (spec/validate-system! {:id :bad
                                  :components {:x {:feature :x}}})))))

(deftest system-without-connections-passes
  (testing "system with no connections is valid"
    (let [s {:id :simple
             :components {:x {:feature :x :lang :go}}}]
      (is (= s (spec/validate-system! s))))))
