(ns drill.spec-test
  (:require [clojure.test :refer [deftest is testing]]
            [drill.spec :as spec]))

(def good-feature
  {:id   :compute
   :lang :babashka
   :desc "Eval Clojure expressions"
   :cases
   {:eval {:input  [:tuple [:= :eval] :string]
           :output :double
           :examples [{:in [:eval "(+ 1 2)"] :out 3.0}]}}})

(def good-produce
  {:id   :translate
   :desc "Translate NL to Clojure"
   :cases
   {:translate {:input  [:tuple [:= :translate] :string]
                :output :string}}})

(deftest validate-spec-accepts-feature
  (is (= good-feature (spec/validate-spec! good-feature))))

(deftest validate-spec-accepts-produce
  (is (= good-produce (spec/validate-spec! good-produce))))

(deftest validate-spec-rejects-missing-id
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"invalid-spec"
        (spec/validate-spec! (dissoc good-feature :id)))))

(deftest validate-spec-rejects-empty-cases
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"invalid-spec"
        (spec/validate-spec! (assoc good-feature :cases {})))))

(deftest validate-spec-rejects-non-keyword-id
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"invalid-spec"
        (spec/validate-spec! (assoc good-feature :id "compute")))))

(deftest validate-spec-rejects-case-without-input
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"invalid-spec"
        (spec/validate-spec!
         (assoc-in good-feature [:cases :eval] {:output :double})))))
