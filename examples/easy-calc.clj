(ns easy-calc
  (:require [nilify.core :as nilify]))

(def spec-query  [:map [:query :string]])
(def spec-expr   [:map [:expr :string]])
(def spec-result [:map [:result :double]])

(def root (nilify/root
           [[:system
             {:id :sys/easy-calc
              :tech "tui babashka"
              :desc (nilify/prompt
                     "A TUI calculator that takes natural language math"
                     "queries, translates them to Clojure expressions,"
                     "and evaluates them.")}
             [:layer
              [:feature
               {:id :feat/ui
                :desc (nilify/prompt
                       "A TUI built with babashka's built-in tools."
                       "Manages a single input field, a confirm dialog, a busy"
                       "indicator, and a result display.")
                :internals {:screens
                            {:query   "Input field for natural language query"
                             :confirm "Shows translated expression, yes/no"
                             :result  "Displays computed result"
                             :busy    "Spinner while waiting"}}}]]
             [:layer
              [:feature
               {:id :feat/translate
                :desc (nilify/prompt
                       "Translate natural-language queries into"
                       "Clojure-computable expressions.")
                :internals {"examples"
                            [{"two plus two" "(+ 2 2)"}
                             {"square root of 16" "(Math/sqrt 16)"}]}}]
              [:feature
               {:id :feat/compute
                :desc (nilify/prompt
                       "Evaluate a Clojure expression string"
                       "in a babashka sci sandbox.")}]]]]))
