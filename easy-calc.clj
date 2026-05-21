(ns easy-calc
  (:require [drill.core :as drill]))

(def ui
  (drill/feature
   {:id :ui
    :lang :babshhka
    :input [:or
            [:enum :get+wait-query]
            [:tuple [:enum :in-progress] :boolean]
            [:tuple [:enum :set-result :confirm+wait-query] :string]]
    :output [:or
             [:tuple [:enum :get+wait-query] :string]
             [:tuple [:enum :set-result :confirm+wait-query :in-progress] :boolean]]
    :desc (drill/prompt "A TUI, using the build in babshhka tools."
                        "It has multiple fields:"
                        "- Input field for the user to input data. The result gets queried by :get+wait-query."
                        "- Confirm dialog when :configm+wait-query is called with the parameter."
                        "- ")}))

(def process
  (drill/produce
   {:id :produce
    ;; :input :string ;; omitted defaults
    ;; :output :string ;; ommited defaults
    :desc "Translate the natural language query into a clojure computable form."}))

(def compute
  (drill/feature
   {:id :compute
    :lang :babashka
    ;;:input :string
    :output :double
    :desc "Use babashka sci to compute the provided string in a sandbox"}))

(def drill-main
  (fn [& args]
    (loop [exit?]
      (if-not exit?
        (let [[_ query] (ui :get+wait-query)
              _ (ui :in-progress true)
              calculation (process query)
              _ (ui :in-progress false)
              [_ confirmed?] (ui [:confirm+wait-query calculation])]
          (if confirmed?
            (do
              (ui :in-progress true)
              (ui [:set-result (compute calculation)])
              (ui :in-progress false))
            (recur false)))
        (println "Bye")))))

(drill/reg-main drill-main)

