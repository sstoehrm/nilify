(ns drill.spec
  (:require [malli.core :as m]
            [malli.error :as me]))

(def Case
  [:map
   [:input  any?]
   [:output any?]
   [:desc     {:optional true} :string]
   [:examples {:optional true}
    [:vector [:map [:in any?] [:out any?]]]]])

(def Spec
  [:map
   [:id    :keyword]
   [:lang  {:optional true} [:enum :babashka :clojure]]
   [:desc  {:optional true} :string]
   [:cases [:and
            [:map-of :keyword Case]
            [:fn {:error/message "must have at least one case"}
             (fn [m] (pos? (count m)))]]]])

(defn validate-spec! [spec]
  (if (m/validate Spec spec)
    spec
    (throw (ex-info "Invalid drill spec: invalid-spec"
                    {:type   :drill/invalid-spec
                     :spec   spec
                     :errors (me/humanize (m/explain Spec spec))}))))
