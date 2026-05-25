(ns nil.validate
  (:require [malli.core :as m]
            [malli.generator :as mg]))

(defn- check-one-example [case-name case-spec example]
  (let [{:keys [in out]} example
        input-ok  (m/validate (:input case-spec) in)
        output-ok (m/validate (:output case-spec) out)]
    (cond
      (not input-ok)
      {:case case-name :in in :out out :status :fail :failure :input-mismatch}

      (not output-ok)
      {:case case-name :in in :out out :status :fail :failure :output-mismatch}

      :else
      {:case case-name :in in :out out :status :pass})))

(defn check-examples [{:keys [id cases]}]
  (let [results (vec (for [[case-name case-spec] cases
                           example (:examples case-spec)]
                       (check-one-example case-name case-spec example)))]
    {:id id
     :check :examples
     :status (if (every? #(= :pass (:status %)) results) :pass :fail)
     :results results}))

(def ^:private sample-count 20)

(defn- check-one-connection [system features [source sink]]
  (let [source-component (first source)
        sink-component   (first sink)
        source-feature-id (:feature (get (:components system) source-component))
        sink-feature-id   (:feature (get (:components system) sink-component))
        source-feature    (get features source-feature-id)
        sink-feature      (get features sink-feature-id)]
    (cond
      (not source-feature)
      {:from source :to sink :status :fail :failure :missing-feature
       :detail (str "feature " source-feature-id " not found")}

      (not sink-feature)
      {:from source :to sink :status :fail :failure :missing-feature
       :detail (str "feature " sink-feature-id " not found")}

      :else
      (let [source-case (get-in source-feature [:cases (second source)])
            sink-case   (get-in sink-feature [:cases (second sink)])
            out-schema  (:output source-case)
            in-schema   (:input sink-case)]
        (try
          (let [samples  (repeatedly sample-count #(mg/generate out-schema))
                failures (remove #(m/validate in-schema %) samples)]
            (if (empty? failures)
              {:from source :to sink :status :pass}
              {:from source :to sink :status :fail :failure :incompatible
               :sample (first failures)}))
          (catch Exception e
            {:from source :to sink :status :fail :failure :generation-error
             :detail (ex-message e)}))))))

(defn check-connections [system features]
  (let [connections (or (:connections system) [])
        results (vec (map #(check-one-connection system features %) connections))]
    {:id (:id system)
     :check :connections
     :status (if (every? #(= :pass (:status %)) results) :pass :fail)
     :results results}))
