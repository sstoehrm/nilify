(ns drill.produce
  (:require [clojure.edn :as edn]
            [malli.core  :as m]
            [malli.error :as me]
            [drill.generator :as gen]))

(defn- validate! [schema value error-type id tag]
  (when-not (m/validate schema value)
    (throw (ex-info (str (name error-type) ": " id "/" tag)
                    {:type  error-type
                     :id    id
                     :tag   tag
                     :value value
                     :error (me/humanize (m/explain schema value))}))))

(defn- build-prompt [{:keys [id desc cases]} tag packed]
  (let [case-spec (get cases tag)]
    (str "You are implementing the case " tag " of the produce " id ".\n"
         (when desc           (str "Unit description: " desc "\n"))
         (when (:desc case-spec) (str "Case description: " (:desc case-spec) "\n"))
         "Input (validated): " (pr-str packed) "\n"
         "Respond with the output value as EDN, no commentary, no fences.")))

(defn call [{:keys [id cases] :as spec} tag args]
  (let [case-spec (get cases tag)]
    (when-not case-spec
      (throw (ex-info (str "unknown-case: " id "/" tag)
                      {:type :drill/unknown-case :id id :tag tag})))
    (let [packed   (vec (cons tag args))]
      (validate! (:input case-spec) packed :drill/input-invalid id tag)
      (let [response (gen/*llm-call* (build-prompt spec tag packed))
            result   (edn/read-string response)]
        (validate! (:output case-spec) result :drill/output-invalid id tag)
        result))))

(defn make-callable [spec]
  (fn [tag & args] (call spec tag args)))
