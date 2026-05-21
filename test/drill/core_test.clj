(ns drill.core-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [drill.core :as drill]
            [drill.registry :as reg]
            [drill.generator :as gen]))

(use-fixtures :each (fn [t] (reg/clear!) (t)))

(def feature-spec
  {:id :compute :lang :babashka
   :cases {:eval {:input [:tuple [:= :eval] :string] :output :double}}})

(def produce-spec
  {:id :translate
   :cases {:translate {:input [:tuple [:= :translate] :string] :output :string}}})

(deftest prompt-joins-with-newline
  (is (= "a\nb\nc" (drill/prompt "a" "b" "c"))))

(deftest feature-registers-spec
  (reg/register-impl! :compute (fn [_] 0.0))
  (drill/feature feature-spec)
  (is (= :feature (:kind (reg/lookup-spec :compute)))))

(deftest feature-returns-callable-in-run-mode
  (reg/register-impl! :compute (fn [[_ s]] (Double/parseDouble s)))
  (let [f (drill/feature feature-spec)]
    (is (= 3.0 (f :eval "3.0")))))

(deftest feature-returns-stub-in-generate-mode
  (swap! reg/state assoc :generate? true)
  (let [f (drill/feature feature-spec)]
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"called-in-generate"
          (f :eval "1.0")))))

(deftest produce-registers-as-produce-kind
  (drill/produce produce-spec)
  (is (= :produce (:kind (reg/lookup-spec :translate)))))

(deftest produce-runs-llm-in-run-mode
  (binding [gen/*llm-call* (constantly "\"(+ 1 2)\"")]
    (let [f (drill/produce produce-spec)]
      (is (= "(+ 1 2)" (f :translate "one plus two"))))))

(deftest reg-main-stores-fn
  (let [f (fn [& _] :ran)]
    (drill/reg-main f)
    (is (= f (:main-fn @reg/state)))))

(deftest feature-rejects-invalid-spec
  (is (thrown-with-msg? clojure.lang.ExceptionInfo #"invalid-spec"
        (drill/feature (dissoc feature-spec :id)))))
