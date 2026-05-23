(ns drill.runtime
  (:require [malli.core  :as m]
            [malli.error :as me]
            [drill.registry :as reg]))

(defn- validate! [schema value error-type id tag]
  (when-not (m/validate schema value)
    (throw (ex-info (str (name error-type) ": " id "/" tag)
                    {:type  error-type
                     :id    id
                     :tag   tag
                     :value value
                     :error (me/humanize (m/explain schema value))}))))

(defn dispatch [{:keys [id cases]} tag args]
  (let [case-spec (get cases tag)]
    (when-not case-spec
      (throw (ex-info (str "unknown-case: " id "/" tag)
                      {:type :drill/unknown-case :id id :tag tag})))
    (let [packed  (vec (cons tag args))
          impl-fn (reg/lookup id)]
      (validate! (:input case-spec) packed :drill/input-invalid id tag)
      (when-not impl-fn
        (throw (ex-info (str "impl-missing: " id)
                        {:type :drill/impl-missing :id id})))
      (let [result (impl-fn packed)]
        (validate! (:output case-spec) result :drill/output-invalid id tag)
        result))))

(defn make-callable [spec]
  (fn [tag & args] (dispatch spec tag args)))

(defn make-stub [spec]
  (fn [& _]
    (throw (ex-info (str "called-in-generate: " (:id spec))
                    {:type :drill/called-in-generate :id (:id spec)}))))
