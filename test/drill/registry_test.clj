(ns drill.registry-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [drill.registry :as reg]))

(use-fixtures :each (fn [t] (reg/clear!) (t)))

(def sample-spec {:id :compute :cases {:eval {:input [:tuple [:= :eval] :string] :output :double}}})

(deftest register-spec-adds-to-descriptions
  (reg/register-spec! sample-spec)
  (is (= sample-spec (get @reg/descriptions :compute))))

(deftest register-spec-rejects-duplicates
  (reg/register-spec! sample-spec)
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"duplicate-id"
        (reg/register-spec! sample-spec))))

(deftest register-impl-adds-to-implementations
  (let [f (fn [_] :ok)]
    (reg/register-impl! :compute f)
    (is (= f (get @reg/implementations :compute)))))

(deftest lookup-returns-impl
  (let [f (fn [_] :ok)]
    (reg/register-impl! :compute f)
    (is (= f (reg/lookup :compute)))
    (is (nil? (reg/lookup :unknown)))))

(deftest lookup-spec-returns-spec
  (reg/register-spec! sample-spec)
  (is (= sample-spec (reg/lookup-spec :compute))))

(deftest clear-empties-all
  (reg/register-spec! sample-spec)
  (reg/register-impl! :compute identity)
  (reg/clear!)
  (is (empty? @reg/descriptions))
  (is (empty? @reg/implementations))
  (is (false? (:generate? @reg/state))))
