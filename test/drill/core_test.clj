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

(deftest list-returns-status-per-spec
  (drill/feature feature-spec)
  (drill/produce produce-spec)
  (let [items (drill/list)
        by-id (into {} (map (juxt :id identity) items))]
    (is (= :missing (-> by-id :compute :status)))
    (is (= :n/a     (-> by-id :translate :status)))
    (is (= :feature (-> by-id :compute :kind)))
    (is (= :produce (-> by-id :translate :kind)))))

(deftest regen-calls-generator-and-marks-fresh
  (let [tmp "test/tmp/drill_generated_core"]
    (.mkdirs (clojure.java.io/file tmp))
    (binding [gen/*generated-dir* tmp
              gen/*llm-call*
              (constantly
               "```clojure\n(ns drill-generated.compute (:require [drill.registry :as r])) (defn -impl [_] 0.0) (r/register-impl! :compute -impl)\n```")]
      (drill/feature feature-spec)
      (drill/regen :compute)
      (let [items   (drill/list)
            compute (first (filter #(= :compute (:id %)) items))]
        (is (= :fresh (:status compute))))
      (doseq [f (.listFiles (clojure.java.io/file tmp))] (.delete f)))))

(deftest main-with-no-args-calls-registered-main-fn
  (let [called (atom nil)]
    (drill/reg-main (fn [& args] (reset! called args)))
    (drill/main "userarg1" "userarg2")
    (is (= '("userarg1" "userarg2") @called))))

(deftest main-with-no-main-and-no-flag-is-a-no-op
  ;; should not throw, should not exit
  (is (nil? (drill/main))))

(deftest diff-handles-missing-generated-file
  (drill/feature feature-spec)
  ;; no file written — diff should return :old-hash nil, :would-regen? true
  (let [result (drill/diff :compute)]
    (is (nil? (:old-hash result)))
    (is (true? (:would-regen? result)))))

(deftest regen-restores-generate-flag-on-failure
  (binding [gen/*llm-call* (fn [_] (throw (ex-info "boom" {:type :test})))]
    (drill/feature feature-spec)
    (is (thrown? clojure.lang.ExceptionInfo (drill/regen :compute)))
    (is (false? (:generate? @reg/state))
        "generate? should be restored to false after generator throws")))
