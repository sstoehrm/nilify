(ns drill.produce-test
  (:require [clojure.test :refer [deftest is testing]]
            [drill.generator :as gen]
            [drill.produce :as produce]))

(def spec
  {:id :translate
   :cases {:translate {:input  [:tuple [:= :translate] :string]
                       :output :string}}})

(deftest call-returns-llm-output-when-valid
  (binding [gen/*llm-call* (constantly "\"(+ 1 2)\"")]
    (let [f (produce/make-callable spec)]
      (is (= "(+ 1 2)" (f :translate "one plus two"))))))

(deftest call-throws-on-invalid-input
  (binding [gen/*llm-call* (constantly "\"x\"")]
    (let [f (produce/make-callable spec)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"input-invalid"
            (f :translate 42))))))

(deftest call-throws-on-invalid-output
  (binding [gen/*llm-call* (constantly "123")]   ; number, not string
    (let [f (produce/make-callable spec)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"output-invalid"
            (f :translate "x"))))))

(deftest call-throws-on-unknown-tag
  (binding [gen/*llm-call* (constantly "\"x\"")]
    (let [f (produce/make-callable spec)]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"unknown-case"
            (f :nope))))))
