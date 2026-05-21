(ns end-to-end-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.java.io :as io]
            [drill.core      :as drill]
            [drill.registry  :as reg]
            [drill.generator :as gen]))

(def tmp "test/tmp/e2e_generated")

(defn- canned-llm [text]
  (cond
    (re-find #":id :ui" text)
    (str "```clojure\n"
         "(ns drill-generated.ui (:require [drill.registry :as r]))\n"
         "(defn -impl [[tag & args]]\n"
         "  (case tag\n"
         "    :get+wait-query     \"what is two plus two\"\n"
         "    :confirm+wait-query true\n"
         "    :set-result         true\n"
         "    :in-progress        true))\n"
         "(r/register-impl! :ui -impl)\n"
         "```")

    (re-find #":id :compute" text)
    (str "```clojure\n"
         "(ns drill-generated.compute\n"
         "  (:require [drill.registry :as r] [sci.core :as sci]))\n"
         "(defn -impl [[_tag expr]] (double (sci/eval-string expr)))\n"
         "(r/register-impl! :compute -impl)\n"
         "```")

    ;; produce runtime call from :translate-query / :translate
    (re-find #"produce :translate-query" text)
    "\"(+ 2 2)\""

    :else
    (throw (ex-info "unexpected prompt" {:prompt text}))))

(use-fixtures :each
  (fn [t]
    (reg/clear!)
    (.mkdirs (io/file tmp))
    (binding [gen/*generated-dir* tmp
              gen/*llm-call*      canned-llm]
      (t))
    (doseq [f (.listFiles (io/file tmp))] (.delete f))))

(deftest generate-then-run-easy-calc
  ;; Stage 1: generate mode. Load the user file in generate? = true.
  (swap! reg/state assoc :generate? true)
  (load-file "easy-calc.clj")
  (drill/regen-stale)

  ;; Verify both feature files exist with fresh status.
  (let [items (drill/list)
        by-id (into {} (map (juxt :id identity) items))]
    (is (= :fresh (-> by-id :ui :status)))
    (is (= :fresh (-> by-id :compute :status)))
    (is (= :n/a   (-> by-id :translate-query :status))))

  ;; Stage 2: run mode. Clear, re-load with generate? false.
  (reg/clear!)
  (swap! reg/state assoc :generate? false)
  (load-file "easy-calc.clj")

  ;; Resolve the callables the user file defined via top-level (def ...).
  (let [ui      @(resolve 'easy-calc/ui)
        compute @(resolve 'easy-calc/compute)]
    (is (= "what is two plus two" (ui :get+wait-query)))
    (is (= 4.0                    (compute :eval "(+ 2 2)")))))
