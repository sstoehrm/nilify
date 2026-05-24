(ns nil.verify
  (:require [malli.core :as m]
            [nil.registry :as reg]))

(defn- check-example [impl-fn case-spec tag example]
  (let [packed  (:in example)
        expected (:out example)]
    (try
      (let [actual (impl-fn packed)]
        (if (not (m/validate (:output case-spec) actual))
          {:tag tag :in packed :expected expected :actual actual
           :failure :schema-violation :status :fail}
          (if (= expected actual)
            {:tag tag :in packed :expected expected :actual actual :status :pass}
            {:tag tag :in packed :expected expected :actual actual
             :failure :mismatch :status :fail})))
      (catch Exception e
        {:tag tag :in packed :expected expected
         :failure :exception :error (ex-message e) :status :fail}))))

(defn verify-spec [{:keys [id cases] :as _spec}]
  (let [impl-fn (reg/lookup id)]
    (if-not impl-fn
      {:id id :status :missing :results []}
      (let [results (vec (for [[tag case-spec] cases
                               example (:examples case-spec)]
                           (check-example impl-fn case-spec tag example)))]
        {:id id
         :status (if (every? #(= :pass (:status %)) results) :pass :fail)
         :results results}))))

(defn verify-all []
  (vec (for [spec (vals (reg/all-specs))]
         (verify-spec spec))))
