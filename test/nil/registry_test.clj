(ns nil.registry-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [nil.registry :as reg]))

(use-fixtures :each (fn [f] (reg/clear!) (f)))

(deftest register-and-lookup-spec
  (testing "registering a spec makes it retrievable"
    (let [s {:id :foo :cases {:x {:input :any :output :any}}}]
      (reg/register-spec! s)
      (is (= s (reg/lookup-spec :foo))))))

(deftest register-and-lookup-impl
  (testing "registering an impl makes it retrievable"
    (let [f (fn [_] 42)]
      (reg/register-impl! :foo f)
      (is (= f (reg/lookup :foo))))))

(deftest duplicate-spec-throws
  (testing "registering the same id twice throws"
    (reg/register-spec! {:id :dup :cases {:x {:input :any :output :any}}})
    (is (thrown-with-msg? Exception #"duplicate-id"
          (reg/register-spec! {:id :dup :cases {:x {:input :any :output :any}}})))))

(deftest clear-resets-all
  (testing "clear! removes all specs and impls"
    (reg/register-spec! {:id :foo :cases {:x {:input :any :output :any}}})
    (reg/register-impl! :foo identity)
    (reg/clear!)
    (is (nil? (reg/lookup-spec :foo)))
    (is (nil? (reg/lookup :foo)))))

(deftest all-specs-returns-registered
  (testing "all-specs returns all registered specs"
    (reg/register-spec! {:id :a :cases {:x {:input :any :output :any}}})
    (reg/register-spec! {:id :b :cases {:y {:input :any :output :any}}})
    (is (= #{:a :b} (set (keys (reg/all-specs)))))))
