(ns nil.verify-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [nil.verify :as verify]
            [nil.registry :as reg]))

(use-fixtures :each (fn [f] (reg/clear!) (f)))

(deftest verify-passing-impl
  (testing "verify returns :pass when all examples match"
    (let [spec {:id :math
                :cases {:add {:input [:tuple [:= :add] :int :int]
                              :output :int
                              :examples [{:in [:add 1 2] :out 3}
                                         {:in [:add 0 0] :out 0}]}}}]
      (reg/register-spec! spec)
      (reg/register-impl! :math (fn [[_tag a b]] (+ a b)))
      (let [result (verify/verify-spec spec)]
        (is (= :pass (:status result)))
        (is (= :math (:id result)))
        (is (= 2 (count (:results result))))))))

(deftest verify-failing-example
  (testing "verify returns :fail when an example mismatches"
    (let [spec {:id :bad
                :cases {:x {:input [:tuple [:= :x] :int]
                            :output :int
                            :examples [{:in [:x 1] :out 999}]}}}]
      (reg/register-spec! spec)
      (reg/register-impl! :bad (fn [[_tag n]] n))
      (let [result (verify/verify-spec spec)]
        (is (= :fail (:status result)))
        (let [r (first (:results result))]
          (is (= 999 (:expected r)))
          (is (= 1 (:actual r))))))))

(deftest verify-no-examples-passes
  (testing "verify returns :pass when spec has no examples"
    (let [spec {:id :empty
                :cases {:x {:input [:tuple [:= :x]] :output :any}}}]
      (reg/register-spec! spec)
      (reg/register-impl! :empty (fn [_] nil))
      (let [result (verify/verify-spec spec)]
        (is (= :pass (:status result)))
        (is (zero? (count (:results result))))))))

(deftest verify-missing-impl
  (testing "verify returns :missing when no impl is registered"
    (let [spec {:id :noimpl
                :cases {:x {:input [:tuple [:= :x]] :output :any}}}]
      (reg/register-spec! spec)
      (let [result (verify/verify-spec spec)]
        (is (= :missing (:status result)))))))

(deftest verify-output-schema-violation
  (testing "verify returns :fail when output doesn't match schema"
    (let [spec {:id :typed
                :cases {:x {:input [:tuple [:= :x]]
                            :output :int
                            :examples [{:in [:x] :out 42}]}}}]
      (reg/register-spec! spec)
      (reg/register-impl! :typed (fn [_] "not-an-int"))
      (let [result (verify/verify-spec spec)]
        (is (= :fail (:status result)))
        (is (= :schema-violation (:failure (first (:results result)))))))))

(deftest verify-all-returns-results-for-all-specs
  (testing "verify-all checks every registered spec"
    (reg/register-spec! {:id :a :cases {:x {:input [:tuple [:= :x]] :output :any}}})
    (reg/register-spec! {:id :b :cases {:y {:input [:tuple [:= :y]] :output :any}}})
    (reg/register-impl! :a (fn [_] nil))
    (reg/register-impl! :b (fn [_] nil))
    (let [results (verify/verify-all)]
      (is (= 2 (count results)))
      (is (= #{:a :b} (set (map :id results)))))))
