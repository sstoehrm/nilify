(ns nil.spec-test
  (:require [clojure.test :refer [deftest is testing]]
            [nil.spec :as spec]))

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
             :cases {:x {:input [:map [:q :string]] :output [:map [:r :double]]}}}]
      (is (= s (spec/validate-spec! s))))))
