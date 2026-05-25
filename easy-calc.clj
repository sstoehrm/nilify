(ns easy-calc
  (:require [nil.core :as nilc]))

(def ui
  (nilc/feature
   {:id :ui
    :desc (nilc/prompt
           "A TUI built with babashka's built-in tools."
           "Manages a single input field, a confirm dialog, a busy"
           "indicator, and a result display.")
    :cases
    {:get+wait-query
     {:input  [:tuple [:= :get+wait-query]]
      :output :string
      :desc   "Block until the user submits a query; return what they typed."}

     :confirm+wait-query
     {:input  [:tuple [:= :confirm+wait-query] :string]
      :output :boolean
      :desc   "Show the value to the user; return their yes/no."}

     :set-result
     {:input  [:tuple [:= :set-result] :string]
      :output :boolean
      :desc   "Display the computed result; ack with true on success."}

     :in-progress
     {:input  [:tuple [:= :in-progress] :boolean]
      :output :boolean
      :desc   "Toggle the busy indicator; ack with true."}}}))

(def translate
  (nilc/produce
   {:id :translate-query
    :desc "Translate natural-language queries into Clojure-computable forms."
    :cases
    {:translate
     {:input  [:tuple [:= :translate] :string]
      :output :string
      :examples [{:in [:translate "what is two plus two"] :out "(+ 2 2)"}]}}}))

(def compute
  (nilc/feature
   {:id :compute
    :lang :babashka
    :desc "Evaluate a Clojure expression string in a babashka sci sandbox."
    :cases
    {:eval
     {:input  [:tuple [:= :eval] :string]
      :output :double
      :examples [{:in [:eval "(+ 1 2)"] :out 3.0}
                 {:in [:eval "(* 2.5 4)"] :out 10.0}]}}}))

(defn run [& _]
  (nilc/system
   {:id :main-loop
    :lang :babashka}
   (fn []
     (loop []
       (let [query (ui :get+wait-query)]
         (ui :in-progress true)
         (let [expr (translate :translate query)]
           (ui :in-progress false)
           (if (ui :confirm+wait-query expr)
             (do (ui :in-progress true)
                 (ui :set-result (str (compute :eval expr)))
                 (ui :in-progress false)
                 (recur))
             (recur))))))))

(nilc/reg-main run)

(when (= *file* (System/getProperty "babashka.file"))
  (apply nilc/main *command-line-args*))
