(ns todo
  (:require [nil.core :as nilc]))

(def spec-todo
  [:map
   {:registry {::id :int}}
   ::id
   [:name :string]
   [:body :string]
   [:priority [:enum :high :medium :low]]
   [:status [:enum :new :in-progress :done]]])

(def root (nilc/root
           [[:system
             {:id :frontend
              :lang :typescript
              :techstack "react"
              :connects-to #{[:backend :backend-api]}}
             [:layer
              [:feature
               {:id :search-ui
                :desc (nilc/prompt
                       "Search frame input field")
                :internals {:query-language
                            {"#id" "search for <id>"
                             "<priority" "search for all <priorities"
                             "*status" "search for a *status"
                             :else "Fulltext search on everything"}}}]
              [:feature
               {:id :todo-list-ui
                :desc (nilc/prompt
                       "List with all of the todos with ::id, :name, :priority and :status, filtered by the :search-ui")}]
              [:feature
               {:id :todo-editor
                :desc (nilc/prompt
                       "Editing the todo for all fields excl. ::id.")}]]]
            [:system
             {:id :backend
              :lang :babashka
              :techstack "http-server"
              :desc (nilc/prompt
                     "A TUI built with babashka's built-in tools."
                     "Manages a single input field, a confirm dialog, a busy"
                     "indicator, and a result display.")
              :provides
              {["HTTP GET" ["/"]]
               {:interface :backend-api
                :input []
                :output [:vector-of spec-todo]}
               ["HTTP POST" ["/"]]
               {:interface :backend-api
                :input spec-todo
                :output []}
               ["HTTP UPDATE" ["/" ::id]]
               {:interface :backend-api
                :input spec-todo
                :output []}}
              [:layer
               [:feature
                {:id :api
                 :desc (nilc/prompt "Provides interfaces :backend-api")}]
               [:feature
                {:id :database
                 :tech "sqlite"
                 :desc (nilc/prompt
                        "Store the :domain-model inside of sqlite")}]]
              [:layer
               [:feature
                {:id :domain-model
                 :internals {"domain-model" spec-todo}}]]}]]))

