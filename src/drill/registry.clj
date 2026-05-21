(ns drill.registry)

(defonce descriptions    (atom {}))
(defonce implementations (atom {}))
(defonce state           (atom {:generate? false :main-fn nil}))

(defn register-spec! [{:keys [id] :as spec}]
  (when (contains? @descriptions id)
    (throw (ex-info (str "duplicate-id: " id)
                    {:type :drill/duplicate-id :id id})))
  (swap! descriptions assoc id spec)
  spec)

(defn register-impl! [id impl-fn]
  (swap! implementations assoc id impl-fn)
  impl-fn)

(defn lookup      [id] (get @implementations id))
(defn lookup-spec [id] (get @descriptions    id))

(defn clear! []
  (reset! descriptions {})
  (reset! implementations {})
  (reset! state {:generate? false :main-fn nil}))
