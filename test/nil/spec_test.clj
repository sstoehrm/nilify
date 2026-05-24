(ns nil.spec-test
  (:require [clojure.test :refer [deftest is testing]]
            [nil.spec :as spec]))

(deftest valid-spec-passes
  (testing "a well-formed spec passes validation"
    (let [s {:id :compute
             :desc "evaluate expressions"
             :cases {:eval {:input [:tuple [:= :eval] :string]
                            :output :double
                            :examples [{:in [:eval "(+ 1 2)"] :out 3.0}]}}}]
      (is (= s (spec/validate-spec! s))))))

(deftest spec-requires-id
  (testing "spec without :id throws"
    (is (thrown-with-msg? Exception #"invalid-spec"
          (spec/validate-spec! {:cases {:x {:input :any :output :any}}})))))

(deftest spec-requires-cases
  (testing "spec without :cases throws"
    (is (thrown-with-msg? Exception #"invalid-spec"
          (spec/validate-spec! {:id :broken})))))

(deftest spec-requires-at-least-one-case
  (testing "spec with empty :cases throws"
    (is (thrown-with-msg? Exception #"invalid-spec"
          (spec/validate-spec! {:id :broken :cases {}})))))

(deftest spec-with-deps-passes
  (testing "spec with :deps validates"
    (let [s {:id :calc :deps [:translate :compute]
             :cases {:x {:input [:tuple [:= :x] :string] :output :double}}}]
      (is (= s (spec/validate-spec! s))))))

(deftest spec-with-lang-passes
  (testing "spec with :lang validates"
    (let [s {:id :x :lang :babashka
             :cases {:x {:input [:tuple [:= :x]] :output :any}}}]
      (is (= s (spec/validate-spec! s))))))
