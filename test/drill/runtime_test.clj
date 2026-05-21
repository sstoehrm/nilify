(ns drill.runtime-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [drill.registry :as reg]
            [drill.runtime  :as rt]))

(use-fixtures :each (fn [t] (reg/clear!) (t)))

(def spec
  {:id :compute
   :cases {:eval  {:input [:tuple [:= :eval] :string] :output :double}
           :reset {:input [:tuple [:= :reset]]        :output :boolean}}})

(deftest make-callable-dispatches-and-returns-bare-value
  (reg/register-impl! :compute (fn [[tag arg]]
                                 (case tag
                                   :eval  (Double/parseDouble arg)
                                   :reset true)))
  (let [f (rt/make-callable spec)]
    (is (= 3.0 (f :eval "3.0")))
    (is (= true (f :reset)))))

(deftest invalid-input-throws-typed-error
  (reg/register-impl! :compute (fn [_] 0.0))
  (let [f (rt/make-callable spec)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"input-invalid"
          (f :eval 123)))))

(deftest invalid-output-throws-typed-error
  (reg/register-impl! :compute (fn [_] "not-a-double"))
  (let [f (rt/make-callable spec)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"output-invalid"
          (f :eval "1.0")))))

(deftest unknown-tag-throws
  (reg/register-impl! :compute (fn [_] 0.0))
  (let [f (rt/make-callable spec)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unknown-case"
          (f :nope)))))

(deftest missing-impl-throws
  (let [f (rt/make-callable spec)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"impl-missing"
          (f :eval "1.0")))))

(deftest stub-throws-when-called
  (let [f (rt/make-stub spec)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"called-in-generate"
          (f :eval "1.0")))))
