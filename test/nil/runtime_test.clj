(ns nil.runtime-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [nil.runtime :as runtime]
            [nil.registry :as reg]))

(use-fixtures :each (fn [f] (reg/clear!) (f)))

(deftest dispatch-calls-impl
  (testing "dispatch routes to registered impl and returns result"
    (let [spec {:id :math :cases {:add {:input [:tuple [:= :add] :int :int]
                                        :output :int}}}]
      (reg/register-spec! spec)
      (reg/register-impl! :math (fn [[_tag a b]] (+ a b)))
      (is (= 5 (runtime/dispatch spec :add [2 3]))))))

(deftest dispatch-validates-input
  (testing "dispatch throws on invalid input"
    (let [spec {:id :math :cases {:add {:input [:tuple [:= :add] :int :int]
                                        :output :int}}}]
      (reg/register-spec! spec)
      (reg/register-impl! :math (fn [[_tag a b]] (+ a b)))
      (is (thrown-with-msg? Exception #"input-invalid"
            (runtime/dispatch spec :add ["not" "ints"]))))))

(deftest dispatch-validates-output
  (testing "dispatch throws on invalid output"
    (let [spec {:id :bad :cases {:x {:input [:tuple [:= :x]]
                                     :output :int}}}]
      (reg/register-spec! spec)
      (reg/register-impl! :bad (fn [_] "not-an-int"))
      (is (thrown-with-msg? Exception #"output-invalid"
            (runtime/dispatch spec :x []))))))

(deftest dispatch-unknown-case-throws
  (testing "dispatch throws for unknown case tag"
    (let [spec {:id :foo :cases {:x {:input [:tuple [:= :x]] :output :any}}}]
      (reg/register-spec! spec)
      (is (thrown-with-msg? Exception #"unknown-case"
            (runtime/dispatch spec :y []))))))

(deftest dispatch-missing-impl-throws
  (testing "dispatch throws when no impl is registered"
    (let [spec {:id :noimpl :cases {:x {:input [:tuple [:= :x]] :output :any}}}]
      (reg/register-spec! spec)
      (is (thrown-with-msg? Exception #"impl-missing"
            (runtime/dispatch spec :x []))))))

(deftest make-callable-returns-function
  (testing "make-callable returns a function that dispatches by tag"
    (let [spec {:id :math :cases {:add {:input [:tuple [:= :add] :int :int]
                                        :output :int}}}]
      (reg/register-spec! spec)
      (reg/register-impl! :math (fn [[_tag a b]] (+ a b)))
      (let [f (runtime/make-callable spec)]
        (is (= 7 (f :add 3 4)))))))
