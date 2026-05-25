(ns nil.spec
  (:require [malli.core :as m]
            [malli.error :as me]))

(def Case
  [:map
   [:input  :any]
   [:output :any]
   [:desc     {:optional true} :string]
   [:examples {:optional true}
    [:vector [:map [:in :any] [:out :any]]]]])

(def FeatureSpec
  [:map
   [:id    :keyword]
   [:lang  {:optional true} :keyword]
   [:desc  {:optional true} :string]
   [:deps  {:optional true} [:vector :keyword]]
   [:cases [:and
            [:map-of :keyword Case]
            [:fn {:error/message "must have at least one case"}
             (fn [m] (pos? (count m)))]]]])

(defn validate-spec! [spec]
  (if (m/validate FeatureSpec spec)
    spec
    (throw (ex-info "invalid-spec"
                    {:type   :nil/invalid-spec
                     :spec   spec
                     :errors (me/humanize (m/explain FeatureSpec spec))}))))

(def Component
  [:map
   [:feature :keyword]
   [:lang    :keyword]])

(def Endpoint
  [:tuple :keyword :keyword [:enum :input :output]])

(def Connection
  [:tuple Endpoint Endpoint])

(def SystemSpec
  [:map
   [:id          :keyword]
   [:desc        {:optional true} :string]
   [:components  [:map-of :keyword Component]]
   [:connections {:optional true} [:vector Connection]]])

(defn validate-system! [system]
  (if (m/validate SystemSpec system)
    system
    (throw (ex-info "invalid-system"
                    {:type   :nil/invalid-system
                     :system system
                     :errors (me/humanize (m/explain SystemSpec system))}))))
