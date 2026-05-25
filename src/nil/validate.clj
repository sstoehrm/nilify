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
