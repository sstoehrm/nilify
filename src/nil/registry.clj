(ns nil.registry)

(defonce specs           (atom {}))
(defonce implementations (atom {}))

(defn register-spec! [{:keys [id] :as spec}]
  (when (contains? @specs id)
    (throw (ex-info (str "duplicate-id: " id)
                    {:type :nil/duplicate-id :id id})))
  (swap! specs assoc id spec)
  spec)

(defn register-impl! [id impl-fn]
  (swap! implementations assoc id impl-fn)
  impl-fn)

(defn lookup      [id] (get @implementations id))
(defn lookup-spec [id] (get @specs id))
(defn all-specs   []   @specs)

(defn clear! []
  (reset! specs {})
  (reset! implementations {}))
