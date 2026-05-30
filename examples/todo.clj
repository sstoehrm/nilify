(ns todo
  (:require [nilify.core :as nilify]))

(def spec-todo
  [:map
   {:registry {:domain-model/id :int}}
   :domain-model/id
   [:name :string]
   [:body :string]
   [:priority [:enum :high :medium :low]]
   [:status [:enum :new :in-progress :done]]])

(nilify/root
 [[:system
   {:id :sys/frontend
    :tech "react"
    :connects-to #{[:sys/backend :iface/backend-api]}}
   [:layer
    [:feature
     {:id :feat/search-ui
      :desc (nilify/prompt
             "Search frame input field")
      :internals {:query-language
                  {"#id" "search for <id>"
                   "<priority" "search for all <priorities"
                   "*status" "search for a *status"
                   :else "Fulltext search on everything"}}}]
    [:feature
     {:id :feat/todo-list-ui
      :desc (nilify/prompt
             "List with all of the todos with :domain-model/id, :name, :priority and :status, filtered by the :feat/search-ui")}]
    [:feature
     {:id :feat/todo-editor
      :desc (nilify/prompt
             "Editing the todo for all fields excl. :domain-model/id.")}]]]
  [:system
   {:id :sys/backend
    :tech "http-server babashka"
    :desc (nilify/prompt
           "HTTP server providing CRUD operations for todos.")
    :provides
    [:iface/backend-api]}
   [:subsystem
    {:id :sub/main
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
       :desc (nilify/prompt "Provides :iface/backend-api")}]
     [:feature
      {:id :feat/database
       :tech "sqlite"
       :desc (nilify/prompt
              "Store the :feat/domain-model inside of sqlite")}]]
    [:layer
     [:feature
      {:id :feat/domain-model
       :internals {"domain-model" spec-todo}}]]]]])
