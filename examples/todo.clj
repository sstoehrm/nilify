(ns todo
  (:require [nil.core :as nilc]))

(def spec-todo
  [:map
   {:registry {:domain-model/id :int}}
   :domain-model/id
   [:name :string]
   [:body :string]
   [:priority [:enum :high :medium :low]]
   [:status [:enum :new :in-progress :done]]])

(def root (nilc/root
           [[:system
             {:id :sys/frontend
              :tech "react"
              :connects-to #{[:sys/backend :iface/backend-api]}}
             [:layer
              [:feature
               {:id :feat/search-ui
                :desc (nilc/prompt
                       "Search frame input field")
                :internals {:query-language
                            {"#id" "search for <id>"
                             "<priority" "search for all <priorities"
                             "*status" "search for a *status"
                             :else "Fulltext search on everything"}}}]
              [:feature
               {:id :feat/todo-list-ui
                :desc (nilc/prompt
                       "List with all of the todos with :domain-model/id, :name, :priority and :status, filtered by the :feat/search-ui")}]
              [:feature
               {:id :feat/todo-editor
                :desc (nilc/prompt
                       "Editing the todo for all fields excl. :domain-model/id.")}]]]
            [:system
             {:id :sys/backend
              :tech "http-server babashka"
              :desc (nilc/prompt
                     "HTTP server providing CRUD operations for todos.")
              :provides
              {["HTTP GET" ["/"]]
               {:interface :iface/backend-api
                :input []
                :output [:vector spec-todo]}
               ["HTTP POST" ["/"]]
               {:interface :iface/backend-api
                :input spec-todo
                :output []}
               ["HTTP UPDATE" ["/" :domain-model/id]]
               {:interface :iface/backend-api
                :input spec-todo
                :output []}}}
             [:layer
              [:feature
               {:id :feat/api
                :desc (nilc/prompt "Provides :iface/backend-api")}]
              [:feature
               {:id :feat/database
                :tech "sqlite"
                :desc (nilc/prompt
                       "Store the :feat/domain-model inside of sqlite")}]]
             [:layer
              [:feature
               {:id :feat/domain-model
                :internals {"domain-model"" spec-todo}}]]]]))
